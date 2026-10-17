package dts.com.examination.application.service;

import dts.com.examination.api.response.*;
import dts.com.examination.application.exception.*;
import dts.com.examination.application.service.selection.PublishedExamSnapshot;
import dts.com.examination.domain.entity.*;
import dts.com.examination.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentExamReadService {
    private final ExamVersionRepository versions;
    private final ExamSessionRepository sessions;
    private final CurrentStudentProfileReader profiles;
    private final PublishedExamSnapshotReader snapshots;
    private final ExamParticipationPolicy participation;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StudentExamListResponse list(UUID userId, String tab, String requestedLicense, int page, int size) {
        if (tab == null || !Set.of("OPEN", "UPCOMING", "PARTICIPATED").contains(tab)
                || page < 0 || page > 1_000_000 || size < 1 || size > 100) throw bad("Invalid tab, page or size");
        String license = license(userId, requestedLicense);
        Instant now = clock.instant();
        var result = versions.findStudentVersions(userId, license, tab, now, PageRequest.of(page, size));
        List<UUID> ids = result.getContent().stream().map(ExamVersion::getId).toList();
        Map<UUID, List<ExamSession>> history = ids.isEmpty() ? Map.of()
                : sessions.findByExamVersionIdInAndUserId(ids, userId).stream().collect(Collectors.groupingBy(ExamSession::getExamVersionId));
        Set<UUID> publicIds = "PARTICIPATED".equals(tab) && !ids.isEmpty()
                ? new HashSet<>(versions.findStudentPublicVersionIds(ids)) : new HashSet<>(ids);
        var items = result.getContent().stream().map(version -> summary(version, snapshots.read(version),
                history.getOrDefault(version.getId(), List.of()), license, now, publicIds.contains(version.getId()))).toList();
        return new StudentExamListResponse(license, tab, page, size, result.getTotalElements(), result.getTotalPages(), items, now);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StudentExamDetailResponse detail(UUID userId, UUID examId, UUID versionId) {
        String license = license(userId, null);
        Instant now = clock.instant();
        var candidates = versions.findStudentDetailVersions(examId, versionId, userId, license);
        if (candidates.isEmpty()) throw new ResourceNotFoundException("Official examination not found");
        ExamVersion version = candidates.get(0);
        PublishedExamSnapshot snapshot = snapshots.read(version);
        var history = sessions.findByExamVersionIdInAndUserId(List.of(version.getId()), userId);
        // The query returns public versions or versions with this user's own history.
        // Public visibility is selected separately so HIDDEN parents cannot offer a new attempt.
        boolean visible = versions.isStudentVersionPublic(version.getId());
        var config = snapshot.criteria().getCriteria();
        Object instructions = version.getMetadata() == null ? null : version.getMetadata().get("instructions");
        return new StudentExamDetailResponse(summary(version, snapshot, history, license, now, visible),
                instructions instanceof String text ? text : null, StudentExamRulesResponse.from(snapshot.rule()),
                new StudentExamDetailResponse.GradingSummary(config.getTotalScore(), config.getPassScore(),
                        config.getGradingMethod().name(), snapshot.policy().passingCorrectAnswers(), snapshot.policy().criticalQuestionCount()), now);
    }

    private StudentExamSummaryResponse summary(ExamVersion version, PublishedExamSnapshot snapshot,
            List<ExamSession> history, String license, Instant now, boolean visible) {
        var state = participation.assess(version, snapshot, history, now, visible,
                license.equals(snapshot.profile().licenseClass()));
        return new StudentExamSummaryResponse(version.getExamId(), version.getId(), version.getTitle(),
                snapshot.profile().licenseClass(), version.getExamType(), snapshot.profile().bankVersion(),
                version.getStartedAt(), version.getEndedAt(), snapshot.policy().totalQuestions(), snapshot.rule().getDurationSeconds(),
                state.maxAttempts(), state.usedAttempts(), state.remainingAttempts(), state.availability(),
                state.participationStatus(), state.activeSessionId(), state.canStart(), state.canResume(), state.reasonCode(), state.retryAvailableAt());
    }

    private String license(UUID userId, String requested) {
        String actual = profiles.selectedLicenseClass(userId);
        if (requested != null && !actual.equals(requested.trim().toUpperCase(Locale.ROOT))) {
            throw new ExaminationException(HttpStatus.FORBIDDEN, "LICENSE_CLASS_MISMATCH", "Requested license class must match Identity");
        }
        return actual;
    }
    private ExaminationException bad(String message) { return new ExaminationException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message); }
}
