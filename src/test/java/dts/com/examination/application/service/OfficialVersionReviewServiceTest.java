package dts.com.examination.application.service;

import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.domain.entity.*;
import dts.com.examination.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OfficialVersionReviewServiceTest {
    private final UUID examId=UUID.randomUUID(), versionId=UUID.randomUUID();
    private final UUID author=UUID.randomUUID(), submitter=UUID.randomUUID(), reviewer=UUID.randomUUID();
    private final ExamVersionRepository versions=mock(ExamVersionRepository.class);
    private final ExamRepository exams=mock(ExamRepository.class);
    private final ExamStructureRepository structures=mock(ExamStructureRepository.class);
    private final ExamRuleRepository rules=mock(ExamRuleRepository.class);
    private final ExamCriteriaRepository criteria=mock(ExamCriteriaRepository.class);
    private final OfficialExamVersionValidator validator=mock(OfficialExamVersionValidator.class);
    private final OfficialVersionApprovalFingerprint fingerprints=mock(OfficialVersionApprovalFingerprint.class);
    private final OfficialVersionReviewStore store=mock(OfficialVersionReviewStore.class);
    private final OfficialVersionReviewService service=new OfficialVersionReviewService(
            versions,exams,structures,rules,criteria,validator,fingerprints,store);
    private final UUID sid=UUID.randomUUID(),rid=UUID.randomUUID(),cid=UUID.randomUUID();
    private final ExamVersion version=ExamVersion.builder().id(versionId).examId(examId).status("DRAFT")
            .examType("OFFICIAL").examStructureId(sid).examRuleId(rid).examCriteriaId(cid).build();
    private final Map<String,Object> config=Map.of("officialConfigVersion",1);
    private OfficialVersionReviewStore.Review pending() {
        return new OfficialVersionReviewStore.Review(UUID.randomUUID(),versionId,"IN_REVIEW","approved-hash",
                submitter, Instant.now(),null,null,null);
    }
    @BeforeEach void setUp() {
        version.setCreatedBy(author);
        when(versions.findExamIdByVersionId(versionId)).thenReturn(Optional.of(examId));
        when(exams.findByIdForUpdate(examId)).thenReturn(Optional.of(Exam.builder().id(examId).build()));
        when(versions.findByIdForUpdate(versionId)).thenReturn(Optional.of(version));
        when(structures.findByIdForUpdate(sid)).thenReturn(Optional.of(ExamStructure.builder().id(sid).build()));
        when(rules.findByIdForUpdate(rid)).thenReturn(Optional.of(ExamRule.builder().id(rid).build()));
        when(criteria.findByIdForUpdate(cid)).thenReturn(Optional.of(ExamCriteria.builder().id(cid).build()));
        when(validator.validate(eq(version),any(),any(),any(),any(),eq(true))).thenReturn(config);
        when(fingerprints.of(version,config)).thenReturn("approved-hash");
    }
    @Test void submitPersistsProofAndAuditButDoesNotPublish() {
        var review=pending();when(store.latest(versionId)).thenReturn(Optional.empty(),Optional.of(review));
        var result=service.submit(versionId,submitter);
        assertEquals("IN_REVIEW",result.status());
        verify(store).submit(any(UUID.class),eq(versionId),eq("approved-hash"),eq(submitter));
        verify(store).journal(any(UUID.class),eq(versionId),eq("SUBMIT"),eq(submitter),eq("approved-hash"),isNull());
        verify(versions,never()).save(any());
    }
    @Test void authorSubmitterAndLastEditorCannotApprove() {
        when(store.latest(versionId)).thenReturn(Optional.of(pending()));
        assertEquals("SELF_APPROVAL_FORBIDDEN",assertThrows(ExaminationException.class,
                ()->service.approve(versionId,author)).getCode());
        assertEquals("SELF_APPROVAL_FORBIDDEN",assertThrows(ExaminationException.class,
                ()->service.approve(versionId,submitter)).getCode());
        version.setUpdatedBy(reviewer);
        assertEquals("SELF_APPROVAL_FORBIDDEN",assertThrows(ExaminationException.class,
                ()->service.approve(versionId,reviewer)).getCode());
        verify(store,never()).decide(any(),anyString(),any(),any());
    }
    @Test void changedSnapshotDeniesApprovalAndPublication() {
        var review=pending();when(store.latest(versionId)).thenReturn(Optional.of(review));
        when(fingerprints.of(version,config)).thenReturn("changed-hash");
        assertEquals("REVIEW_SNAPSHOT_CHANGED",assertThrows(ExaminationException.class,
                ()->service.approve(versionId,reviewer)).getCode());
        var approved=new OfficialVersionReviewStore.Review(review.id(),versionId,"APPROVED","approved-hash",
                review.submittedBy(),review.submittedAt(),reviewer,Instant.now(),null);
        when(store.latest(versionId)).thenReturn(Optional.of(approved));
        assertEquals("APPROVED_SNAPSHOT_CHANGED",assertThrows(ExaminationException.class,
                ()->service.requireApproved(version,config)).getCode());
        verify(store,never()).decide(any(),anyString(),any(),any());
    }
    @Test void explicitApprovalIsRequiredBeforePublish() {
        when(store.latest(versionId)).thenReturn(Optional.empty());
        assertEquals("APPROVAL_REQUIRED",assertThrows(ExaminationException.class,
                ()->service.requireApproved(version,config)).getCode());
        when(store.latest(versionId)).thenReturn(Optional.of(pending()));
        assertEquals("APPROVAL_REQUIRED",assertThrows(ExaminationException.class,
                ()->service.requireApproved(version,config)).getCode());
    }
    @Test void pendingAndApprovedReviewsLockEdits() {
        when(store.latest(versionId)).thenReturn(Optional.of(pending()));
        assertEquals("VERSION_UNDER_REVIEW",assertThrows(ExaminationException.class,
                ()->service.requireEditable(versionId)).getCode());
    }
    @Test void aDifferentReviewerCanApproveTheUnchangedProof() {
        var review=pending();when(store.latest(versionId)).thenReturn(Optional.of(review));
        assertDoesNotThrow(()->service.approve(versionId,reviewer));
        verify(store).decide(eq(review.id()),eq("APPROVED"),eq(reviewer),isNull());
    }
    @Test void rejectingRequiresReasonAndRemainsAuditable() {
        var review=pending();when(store.latest(versionId)).thenReturn(Optional.of(review));
        assertEquals("REJECTION_REASON_REQUIRED",assertThrows(ExaminationException.class,
                ()->service.reject(versionId,reviewer," ")).getCode());
        assertDoesNotThrow(()->service.reject(versionId,reviewer,"Needs correction"));
        verify(store).decide(review.id(),"REJECTED",reviewer,"Needs correction");
        verify(store).journal(review.id(),versionId,"REJECT",reviewer,review.fingerprint(),"Needs correction");
    }
}
