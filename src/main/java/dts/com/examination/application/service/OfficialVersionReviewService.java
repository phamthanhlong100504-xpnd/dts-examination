package dts.com.examination.application.service;

import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.exception.ResourceNotFoundException;
import dts.com.examination.domain.entity.ExamVersion;
import dts.com.examination.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Approval is a binding to the exact validated OFFICIAL source, not metadata approval. */
@Service
@RequiredArgsConstructor
public class OfficialVersionReviewService {
    private final ExamVersionRepository versions;
    private final ExamRepository exams;
    private final ExamStructureRepository structures;
    private final ExamRuleRepository rules;
    private final ExamCriteriaRepository criteria;
    private final OfficialExamVersionValidator validator;
    private final OfficialVersionApprovalFingerprint fingerprints;
    private final OfficialVersionReviewStore store;

    public record ReviewResponse(UUID reviewId, UUID examVersionId, String status, String fingerprint,
                                 UUID submittedBy, java.time.Instant submittedAt,
                                 UUID decidedBy, java.time.Instant decidedAt, String reason) { }

    private static ReviewResponse response(OfficialVersionReviewStore.Review r) {
        return new ReviewResponse(r.id(), r.versionId(), r.status(), r.fingerprint(),
                r.submittedBy(), r.submittedAt(), r.decidedBy(), r.decidedAt(), r.reason());
    }

    public ReviewResponse current(UUID versionId) {
        versions.findByIdAndDeletedAtIsNull(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam version not found"));
        return store.latest(versionId).map(OfficialVersionReviewService::response).orElse(null);
    }

    /** Called by the existing mutation path while holding the parent/version row locks. */
    public void requireEditable(UUID versionId) {
        store.latest(versionId).ifPresent(review -> {
            if ("IN_REVIEW".equals(review.status()) || "APPROVED".equals(review.status())) {
                throw conflict("VERSION_UNDER_REVIEW", "Reject this review or clone the version before editing");
            }
        });
    }

    /** Maintain future author history without changing existing exam/session tables. */
    public void recordEditor(UUID versionId) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UUID actor)) {
            throw new org.springframework.security.access.AccessDeniedException("Authenticated editor required");
        }
        store.recordEditor(versionId, actor);
    }

    /** Called by the existing publish path BEFORE archiving a previously published version. */
    public void requireApproved(ExamVersion version, Map<String, Object> verifiedConfig) {
        if (!"OFFICIAL".equals(version.getExamType())) return; // Preserve existing non-official versions.
        String proof = fingerprints.of(version, verifiedConfig);
        var latest = store.latest(version.getId())
                .orElseThrow(() -> conflict("APPROVAL_REQUIRED", "Official version must be submitted and approved"));
        if (!"APPROVED".equals(latest.status()))
            throw conflict("APPROVAL_REQUIRED", "Official version has not been approved");
        if (!proof.equals(latest.fingerprint()))
            throw conflict("APPROVED_SNAPSHOT_CHANGED", "Official source or configuration changed after approval");
    }

    public boolean approvalMatches(ExamVersion version, Map<String, Object> verifiedConfig) {
        if (!"OFFICIAL".equals(version.getExamType())) return false;
        String proof = fingerprints.of(version, verifiedConfig);
        return store.latest(version.getId())
                .filter(review -> "APPROVED".equals(review.status()) && proof.equals(review.fingerprint()))
                .isPresent();
    }

    @Transactional
    public ReviewResponse submit(UUID versionId, UUID actor) {
        ExamVersion version = lockOfficialDraft(versionId);
        requireEditable(versionId);
        Map<String, Object> verifiedConfig = verified(version);
        UUID reviewId = UUID.randomUUID();
        String fingerprint = fingerprints.of(version, verifiedConfig);
        store.submit(reviewId, versionId, fingerprint, actor);
        store.journal(reviewId, versionId, "SUBMIT", actor, fingerprint, null);
        return response(store.latest(versionId).orElseThrow());
    }

    @Transactional
    public ReviewResponse approve(UUID versionId, UUID actor) {
        ExamVersion version = lockOfficialDraft(versionId);
        var review = pending(versionId);
        if (version.getCreatedBy() == null
                || Objects.equals(actor, review.submittedBy()) || Objects.equals(actor, version.getCreatedBy())
                || Objects.equals(actor, version.getUpdatedBy()) || store.wasEditor(versionId, actor)) {
            throw new ExaminationException(HttpStatus.FORBIDDEN, "SELF_APPROVAL_FORBIDDEN",
                    "A version author, editor or submitter cannot approve its own official examination");
        }
        if (!fingerprints.of(version, verified(version)).equals(review.fingerprint())) {
            throw conflict("REVIEW_SNAPSHOT_CHANGED", "Version or source changed since review submission");
        }
        store.decide(review.id(), "APPROVED", actor, null);
        store.journal(review.id(), versionId, "APPROVE", actor, review.fingerprint(), null);
        return response(store.latest(versionId).orElseThrow());
    }

    @Transactional
    public ReviewResponse reject(UUID versionId, UUID actor, String reason) {
        lockOfficialDraft(versionId);
        var review = pending(versionId);
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new ExaminationException(HttpStatus.BAD_REQUEST, "REJECTION_REASON_REQUIRED",
                    "Rejecting an official version requires a reason of 1–500 characters");
        String safeReason = reason.trim();
        store.decide(review.id(), "REJECTED", actor, safeReason);
        store.journal(review.id(), versionId, "REJECT", actor, review.fingerprint(), safeReason);
        return response(store.latest(versionId).orElseThrow());
    }

    private OfficialVersionReviewStore.Review pending(UUID versionId) {
        var review = store.latest(versionId)
                .orElseThrow(() -> conflict("REVIEW_NOT_PENDING", "No pending review"));
        if (!"IN_REVIEW".equals(review.status())) throw conflict("REVIEW_NOT_PENDING", "No pending review");
        return review;
    }

    private ExamVersion lockOfficialDraft(UUID versionId) {
        UUID examId = versions.findExamIdByVersionId(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam version not found"));
        exams.findByIdForUpdate(examId).orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        ExamVersion version = versions.findByIdForUpdate(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam version not found"));
        if (!"DRAFT".equals(version.getStatus()) || !"OFFICIAL".equals(version.getExamType()))
            throw conflict("VERSION_NOT_REVIEWABLE", "Only draft OFFICIAL versions can be reviewed");
        return version;
    }

    private Map<String, Object> verified(ExamVersion version) {
        // Same dependency lock order as ExamVersionServiceImpl: parent, version, structure, rule, criteria.
        var structure = structures.findByIdForUpdate(version.getExamStructureId())
                .orElseThrow(() -> new ResourceNotFoundException("Exam structure not found"));
        var rule = rules.findByIdForUpdate(version.getExamRuleId())
                .orElseThrow(() -> new ResourceNotFoundException("Exam rule not found"));
        var criterion = version.getExamCriteriaId() == null ? null : criteria.findByIdForUpdate(version.getExamCriteriaId())
                .orElseThrow(() -> new ResourceNotFoundException("Exam criteria not found"));
        return validator.validate(version, structure, rule, criterion, validator.profile(version.getConfigs()), true);
    }

    private static ExaminationException conflict(String code, String message) {
        return new ExaminationException(HttpStatus.CONFLICT, code, message);
    }
}
