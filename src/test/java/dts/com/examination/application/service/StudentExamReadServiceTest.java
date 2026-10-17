package dts.com.examination.application.service;

import dts.com.examination.api.response.*;
import dts.com.examination.application.exception.*;
import dts.com.examination.domain.entity.ExamSession;
import dts.com.examination.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import java.time.*;
import java.util.*;
import static dts.com.examination.application.service.PublishedVersionTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StudentExamReadServiceTest {
    private PublishedVersionTestFixture f;
    private ExamVersionRepository versions;
    private ExamSessionRepository sessions;
    private CurrentStudentProfileReader profiles;
    private StudentExamReadService service;
    private Clock clock;
    private Instant now;
    private List<ExamSession> history;

    @BeforeEach void setup() throws Exception {
        f=new PublishedVersionTestFixture("B");versions=mock(ExamVersionRepository.class);sessions=mock(ExamSessionRepository.class);
        profiles=mock(CurrentStudentProfileReader.class);clock=mock(Clock.class);now=START.plusSeconds(300);history=List.of();
        when(clock.instant()).thenAnswer(c->now);when(profiles.selectedLicenseClass(any(UUID.class))).thenReturn("B");
        when(versions.findStudentVersions(any(),anyString(),anyString(),any(),any())).thenAnswer(c->new PageImpl<>(List.of(f.version),c.getArgument(4),1));
        when(sessions.findByExamVersionIdInAndUserId(anyCollection(),any())).thenAnswer(c->history);
        when(versions.findStudentDetailVersions(any(),any(),any(),anyString())).thenAnswer(c->List.of(f.version));
        when(versions.isStudentVersionPublic(VERSION)).thenReturn(true);
        when(versions.findStudentPublicVersionIds(anyCollection())).thenReturn(List.of(VERSION));
        service=new StudentExamReadService(versions,sessions,profiles,f.reader,new ExamParticipationPolicy(),clock);
    }

    @Test void listUsesTrustedIdentityAndPublishedCountsWithDatabasePagination() {
        var result=service.list(USER,"OPEN",null,0,20);var item=result.items().get(0);
        assertEquals("B",result.selectedLicenseClass());assertEquals(30,item.questionCount());assertEquals(1200,item.durationSeconds());
        assertEquals(1,result.totalElements());assertEquals(1,result.totalPages());assertEquals(now,result.serverTime());
        assertEquals(EXAM,item.examId());assertEquals(VERSION,item.examVersionId());assertEquals("OPEN",item.availability());assertTrue(item.canStart());
        verify(versions).findStudentVersions(USER,"B","OPEN",now,PageRequest.of(0,20));verify(sessions).findByExamVersionIdInAndUserId(List.of(VERSION),USER);
    }
    @Test void invalidTabsAndPaginationAreRejectedBeforeIdentityOrDatabaseAccess() {
        for(String tab:Arrays.asList(null,"CLOSED","open",""))error("BAD_REQUEST",()->service.list(USER,tab,null,0,20));
        for(int page:new int[]{-1,1_000_001})error("BAD_REQUEST",()->service.list(USER,"OPEN",null,page,20));
        for(int size:new int[]{0,101})error("BAD_REQUEST",()->service.list(USER,"OPEN",null,0,size));
        verifyNoInteractions(profiles,versions,sessions);
    }
    @Test void LicenseQueryCannotOverrideIdentitySelection() {
        error("LICENSE_CLASS_MISMATCH",()->service.list(USER,"OPEN","A1",0,20));verifyNoInteractions(versions,sessions);
        assertEquals("B",service.list(USER,"OPEN"," b ",0,20).selectedLicenseClass());
    }
    @Test void emptyDatabasePageIsAValidEmptyResponseAndDoesNotLoadSessions() {
        doReturn(Page.empty(PageRequest.of(0,20))).when(versions).findStudentVersions(any(),anyString(),anyString(),any(),any());
        var result=service.list(USER,"OPEN",null,0,20);assertTrue(result.items().isEmpty());assertEquals(0,result.totalElements());assertEquals(0,result.totalPages());verifyNoInteractions(sessions);
    }
    @Test void openingBoundaryUsesOneServerInstantAndStartInclusiveEndExclusive() {
        now=START.minusNanos(1);assertEquals("UPCOMING",item().availability());assertEquals("EXAM_NOT_STARTED",item().reasonCode());
        now=START;assertEquals("OPEN",item().availability());assertTrue(item().canStart());
        now=END;assertEquals("CLOSED",item().availability());assertFalse(item().canStart());assertEquals("EXAM_CLOSED",item().reasonCode());
    }
    @Test void differentUsersHaveIndependentAttemptCounts() {
        var other=new UUID(0,999);history=List.of(f.session("SUBMITTED",1,START.plusSeconds(1200)));
        assertEquals(1,item().usedAttempts());assertFalse(item().canStart());
        when(sessions.findByExamVersionIdInAndUserId(List.of(VERSION),other)).thenReturn(List.of());
        var otherItem=service.list(other,"OPEN",null,0,20).items().get(0);assertEquals(0,otherItem.usedAttempts());assertTrue(otherItem.canStart());
        verify(sessions).findByExamVersionIdInAndUserId(List.of(VERSION),other);
    }
    @Test void existingInProgressSessionCanResumeEvenWhenItsOnlyAttemptIsConsumed() {
        history=List.of(f.session("IN_PROGRESS",1,now.plusSeconds(60)));var item=item();
        assertEquals(0,item.remainingAttempts().longValue());assertFalse(item.canStart());assertTrue(item.canResume());
        assertEquals(history.get(0).getId(),item.activeSessionId());assertEquals("SESSION_IN_PROGRESS",item.reasonCode());
    }
    @Test void expiredSessionStatusIsComputedWithoutWritingOnGet() {
        f.rule.setAllowRetry(true);f.freezeRule();history=List.of(f.session("IN_PROGRESS",1,now));
        var item=item();assertEquals("EXPIRED",item.participationStatus());assertFalse(item.canResume());assertTrue(item.canStart());
        assertEquals("IN_PROGRESS",history.get(0).getStatus());verify(sessions,never()).save(any());verify(versions,never()).save(any());
    }
    @Test void retryIntervalIsBasedOnCompletionAndOpensAtItsExactBoundary() {
        f.rule.setAllowRetry(true);f.rule.setRetryIntervalSeconds(60);f.freezeRule();
        var attempt=f.session("SUBMITTED",1,now.plusSeconds(200));attempt.setSubmittedAt(now.minusSeconds(30));history=List.of(attempt);
        assertEquals("RETRY_INTERVAL",item().reasonCode());assertEquals(now.plusSeconds(30),item().retryAvailableAt());
        now=now.plusSeconds(30);assertTrue(item().canStart());
    }
    @Test void zeroRetryLimitIsUnlimitedAndLargePositiveLimitDoesNotOverflow() {
        f.rule.setAllowRetry(true);f.freezeRule();var unlimited=item();assertNull(unlimited.maxAttempts());assertNull(unlimited.remainingAttempts());
        f.rule.setMaxRetry(Integer.MAX_VALUE);f.freezeRule();assertEquals(2147483648L,item().maxAttempts().longValue());
        f.rule.setMaxRetry(2);f.freezeRule();assertEquals(3,item().maxAttempts().longValue());
    }
    @Test void softDeletedAttemptStillConsumesQuotaButCannotBeResumed() {
        var attempt=f.session("IN_PROGRESS",1,now.plusSeconds(60));attempt.setDeletedAt(now);history=List.of(attempt);
        var item=item();assertEquals(1,item.usedAttempts());assertFalse(item.canResume());assertNull(item.activeSessionId());assertEquals("ATTEMPTS_EXHAUSTED",item.reasonCode());
    }
    @Test void pausedSessionResumesWithinBothPauseAndResumeLimits() {
        pausedRule();var attempt=f.session("READY",1,now.plusSeconds(60));attempt.setMetadata(Map.of("pausedAt",now.minusSeconds(10).toString(),"pausedRemainingSeconds",50));history=List.of(attempt);
        assertTrue(item().canResume());assertEquals("SESSION_PAUSED",item().reasonCode());assertFalse(item().canStart());
    }
    @Test void expiredPauseCountsAsExpiredAndAllowsAnEligibleRetry() {
        pausedRule();f.rule.setAllowRetry(true);f.freezeRule();var attempt=f.session("READY",1,now.plusSeconds(60));
        attempt.setMetadata(Map.of("pausedAt",now.minusSeconds(30).toString(),"pausedRemainingSeconds",50));history=List.of(attempt);
        assertEquals("EXPIRED",item().participationStatus());assertFalse(item().canResume());assertTrue(item().canStart());
    }
    @Test void multipleActiveSessionsFailClosedInsteadOfOfferingAnotherAttempt() {
        history=List.of(f.session("IN_PROGRESS",1,now.plusSeconds(60)),f.session("IN_PROGRESS",2,now.plusSeconds(60)));
        assertEquals("SESSION_CONFLICT",item().reasonCode());assertFalse(item().canStart());assertFalse(item().canResume());
    }
    @Test void hiddenParentInParticipatedTabCannotOfferANewAttemptButKeepsOwnedRecovery() {
        when(versions.findStudentPublicVersionIds(List.of(VERSION))).thenReturn(List.of());
        history=List.of(f.session("SUBMITTED",1,now.plusSeconds(60)));var closed=service.list(USER,"PARTICIPATED",null,0,20).items().get(0);
        assertEquals("CLOSED",closed.availability());assertEquals("EXAM_UNAVAILABLE",closed.reasonCode());assertFalse(closed.canStart());
        history=List.of(f.session("IN_PROGRESS",1,now.plusSeconds(60)));assertTrue(service.list(USER,"PARTICIPATED",null,0,20).items().get(0).canResume());
    }
    @Test void participatedArchivedVersionKeepsItsOriginalLicenseAfterIdentitySelectionChanges() throws Exception {
        f=new PublishedVersionTestFixture("A1");f.version.setStatus("ARCHIVED");history=List.of(f.session("SUBMITTED",1,now));
        var item=service.list(USER,"PARTICIPATED",null,0,20).items().get(0);assertEquals("A1",item.licenseClass());assertEquals(25,item.questionCount());
        assertFalse(item.canStart());verify(versions).findStudentVersions(USER,"B","PARTICIPATED",now,PageRequest.of(0,20));
    }
    @Test void missingOrForeignExamDetailReturnsNotFoundUsingUserScopedQuery() {
        var other=new UUID(0,888);when(versions.findStudentDetailVersions(other,null,USER,"B")).thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class,()->service.detail(USER,other,null));verify(versions).findStudentDetailVersions(other,null,USER,"B");verifyNoInteractions(sessions);
    }
    @Test void detailReturnsOnlySafeFrozenRulesAndActualOptionalInstructions() {
        f.version.setMetadata(Map.of("instructions","Instructions saved by the administrator"));var response=service.detail(USER,EXAM,VERSION);
        assertEquals(30,response.grading().totalScore());assertEquals(27,response.grading().passScore());assertEquals("FREE",response.rules().navigationMode());
        assertEquals("Instructions saved by the administrator",response.instructions());
        String json=f.mapper.valueToTree(response).toString();assertFalse(json.contains("isCorrect"));assertFalse(json.contains("privateContentForSerializationCheck"));
        assertFalse(json.contains("structureSnapshot"));assertFalse(json.contains("questionIds"));assertFalse(json.contains("selectedAnswer"));
        verify(versions).findStudentDetailVersions(EXAM,VERSION,USER,"B");
    }
    @Test void publishedSnapshotDoesNotReadMutableCurrentRuleObjects() {
        f.rule.setDurationSeconds(99);f.rule.setRequireFullscreen(true);f.structure.getSections().get(0).setQuestionCount(999);
        var detail=service.detail(USER,EXAM,null);assertEquals(1200,detail.rules().durationSeconds());assertEquals(30,detail.exam().questionCount());assertFalse(detail.rules().requireFullscreen());assertNull(detail.instructions());
    }
    @Test void missingOrInconsistentPublicationSnapshotsFailWithStableCode() {
        Object original=f.version.getConfigs().remove("ruleSnapshot");error("VERSION_NOT_READY",this::item);
        f.version.getConfigs().put("ruleSnapshot",original);f.version.getConfigs().put("totalQuestions",31);error("VERSION_NOT_READY",this::item);
        f.version.getConfigs().put("totalQuestions",30);f.version.getConfigs().put("officialConfigVersion",1.5);error("VERSION_NOT_READY",this::item);
    }
    @Test void hiddenDetailNeverOffersStartAndMalformedPauseNeverCreatesASecondAttempt() {
        when(versions.isStudentVersionPublic(VERSION)).thenReturn(false);assertFalse(service.detail(USER,EXAM,null).exam().canStart());
        pausedRule();history=List.of(f.session("READY",1,now.plusSeconds(60)));assertFalse(item().canResume());assertFalse(item().canStart());assertEquals("SESSION_NOT_RESUMABLE",item().reasonCode());
    }
    @Test void repeatedReadIsStableAndPerformsNoPersistenceMutations() {
        var first=service.list(USER,"OPEN",null,0,20);assertEquals(first,service.list(USER,"OPEN",null,0,20));
        verify(versions,never()).save(any());verify(sessions,never()).save(any());verify(sessions,never()).delete(any());
    }

    private StudentExamSummaryResponse item(){return service.list(USER,"OPEN",null,0,20).items().get(0);}
    private void pausedRule(){f.rule.setAllowPause(true);f.rule.setAllowResume(true);f.rule.setMaxPauseCount(1);f.rule.setMaxPauseDurationSeconds(60);f.rule.setResumeTimeoutSeconds(30);f.freezeRule();}
    private void error(String code,Runnable action){var failure=assertThrows(ExaminationException.class,action::run);assertEquals(code,failure.getCode());}
}
