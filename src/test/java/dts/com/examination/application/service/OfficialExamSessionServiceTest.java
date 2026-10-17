package dts.com.examination.application.service;

import dts.com.examination.api.form.*;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.service.selection.FrozenExamQuestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static dts.com.examination.application.service.PublishedVersionTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OfficialExamSessionServiceTest {
    OfficialSessionFixture f;
    @BeforeEach void setup() throws Exception { f=new OfficialSessionFixture(); }

    @Test void automaticAttemptUsesTheRealThirtyQuestionMatrixAndOneCriticalQuestion() {
        var s=f.start();var paper=f.frozen(s);
        assertEquals(30,paper.questions().size());assertEquals(30,paper.questions().stream().map(FrozenExamQuestion::id).distinct().count());
        assertEquals(1,paper.questions().stream().filter(FrozenExamQuestion::isCritical).count());assertEquals(0,s.getVersion());
        verify(f.content).getQuestionBank("dts-2026-600-v1","B");assertEquals(30,f.rows(s).size());
    }
    @Test void a1AttemptUsesItsOwnRealSourceAndPublishedTwentyFiveQuestionPolicy() throws Exception {
        f=new OfficialSessionFixture("A1");var s=f.start();assertEquals(25,f.frozen(s).questions().size());
        assertEquals(1,f.frozen(s).questions().stream().filter(FrozenExamQuestion::isCritical).count());
        assertEquals(f.published.rule.getDurationSeconds(),s.getDurationSeconds());
        verify(f.content).getQuestionBank("dts-2026-600-v1","A1");
    }
    @Test void lastAllowedAttemptResumesBeforeQuotaCheckingWithoutRereadingSource() {
        var s=f.start();Instant expiry=s.getExpiredAt();f.clock.set(f.clock.instant().plusSeconds(20));clearInvocations(f.content);
        var response=f.service.start(f.request(),USER);assertTrue(response.isResumedExisting());assertEquals(s.getId(),response.getSessionId());
        assertEquals(expiry,response.getExpiredAt());assertEquals(1,f.history.size());verifyNoInteractions(f.content);
    }
    @Test void manualPublicationPreservesTheSelectedOrderAndWorksWithoutContentBuilder() {
        var source=f.frozen(f.start());f.manual(source);var s=f.start();
        assertEquals(source.questions().stream().map(FrozenExamQuestion::id).toList(),f.frozen(s).questions().stream().map(FrozenExamQuestion::id).toList());
        verifyNoInteractions(f.content);
    }
    @Test void repeatedPaperReadsPreserveContentOptionOrderAndTimerWithoutLeakingTheKey() throws Exception {
        var s=f.start();clearInvocations(f.content);var first=f.service.paper(s.getId(),USER);var second=f.service.paper(s.getId(),USER);
        assertEquals(first,second);String json=f.published.mapper.writeValueAsString(first);
        assertFalse(json.contains("isCorrect"));assertFalse(json.contains("explanations"));assertFalse(json.contains("officialPaper"));
        verifyNoInteractions(f.content);
    }
    @Test void changingPublishedObjectsAfterStartCannotChangeTheFrozenPaper() {
        var s=f.start();var first=f.service.paper(s.getId(),USER);f.published.structure.getSections().get(0).setQuestionCount(999);
        f.published.rule.setDurationSeconds(1);f.published.version.setConfigs(Map.of());
        assertEquals(first,f.service.paper(s.getId(),USER));assertEquals(1200,f.service.detail(s.getId(),USER).getDurationSeconds());
    }
    @Test void anotherUserCannotReadOrModifyOwnedSession() {
        var s=f.start();var other=new UUID(0,555);
        error("ACCESS_DENIED",()->f.service.paper(s.getId(),other));
        error("ACCESS_DENIED",()->f.service.save(s.getId(),f.saveRequest(s,List.of(f.correct(f.frozen(s).questions().get(0)))),other));
        error("ACCESS_DENIED",()->f.service.submit(s.getId(),"submit-1",other));
    }
    @Test void changedLicenseStillAllowsOwnedRecoveryButCannotStartAnotherLicensedAttempt() {
        var s=f.start();when(f.profiles.selectedLicenseClass(USER)).thenReturn("A1");
        assertEquals(s.getId(),f.service.start(f.request(),USER).getSessionId());
        f.service.submit(s.getId(),"submit-1",USER);error("LICENSE_CLASS_MISMATCH",()->f.start());
    }
    @Test void wrongLicenseCannotStartAnUnownedExamination() {
        when(f.profiles.selectedLicenseClass(USER)).thenReturn("A1");error("LICENSE_CLASS_MISMATCH",()->f.start());verifyNoInteractions(f.content);
    }
    @Test void directDraftVersionIsRejected() {
        f.published.version.setStatus("DRAFT");error("VERSION_NOT_READY",()->f.start());assertTrue(f.history.isEmpty());
    }
    @Test void hiddenParentRejectsNewAttemptsButKeepsOwnedRecovery() {
        f.parent.setStatus("HIDDEN");error("EXAM_UNAVAILABLE",()->f.start());f.parent.setStatus("PUBLISHED");var s=f.start();
        f.parent.setStatus("HIDDEN");assertEquals(s.getId(),f.service.start(f.request(),USER).getSessionId());
    }
    @Test void startBeforeTheWindowOrExactlyAtItsEndIsRejected() {
        f.clock.set(START.minusNanos(1));error("EXAM_NOT_STARTED",()->f.start());
        f.clock.set(END);error("EXAM_CLOSED",()->f.start());assertTrue(f.history.isEmpty());
    }
    @Test void sourceFailureDoesNotConsumeAnAttempt() {
        when(f.content.getQuestionBank("dts-2026-600-v1","B")).thenThrow(new ExaminationException(org.springframework.http.HttpStatus.BAD_GATEWAY,"SOURCE_UNAVAILABLE","Unavailable"));
        error("SOURCE_UNAVAILABLE",()->f.start());assertTrue(f.history.isEmpty());verify(f.sessions,never()).saveAndFlush(any());
    }
    @Test void completeSourceReadMustFinishInsideTheExaminationWindow() {
        when(f.content.getQuestionsBatch(anyList(),eq("B"))).thenAnswer(inv -> {f.clock.set(END);List<UUID> ids=inv.getArgument(0);return ids.stream().map(f.details::get).toList();});
        error("EXAM_CLOSED",()->f.start());assertTrue(f.history.isEmpty());
    }
    @Test void oldOfficialAttemptWithoutSnapshotFailsClosedInsteadOfCallingSource() {
        var s=f.start();s.setMetadata(Map.of());clearInvocations(f.content);
        error("SESSION_SNAPSHOT_MISSING",()->f.service.paper(s.getId(),USER));verifyNoInteractions(f.content);
    }
    @Test void saveRequiresCurrentVersionAndRejectsOlderAutosaveWithoutOverwriting() {
        var s=f.start();var q=f.frozen(s).questions().get(0);var request=f.saveRequest(s,List.of(f.correct(q)));
        assertEquals(1,f.service.save(s.getId(),request,USER).getVersion());
        error("VERSION_CONFLICT",()->f.service.save(s.getId(),request,USER));assertEquals(f.correct(q).getSelectedAnswer(),f.rows(s).get(0).getSelectedAnswer());
    }
    @Test void missingVersionIsNotAcceptedForOfficialAutosave() {
        var s=f.start();var request=f.saveRequest(s,List.of(f.correct(f.frozen(s).questions().get(0))));request.setVersion(null);
        error("VERSION_REQUIRED",()->f.service.save(s.getId(),request,USER));assertEquals(0,s.getVersion());
    }
    @Test void mixedValidAndForeignQuestionsRejectTheEntireBatch() {
        var s=f.start();var valid=f.correct(f.frozen(s).questions().get(0));var foreign=AnswerItemRequest.builder().questionId(new UUID(0,666)).selectedAnswer(valid.getSelectedAnswer()).build();
        error("ANSWER_INVALID",()->f.service.save(s.getId(),f.saveRequest(s,List.of(valid,foreign)),USER));
        assertTrue(f.rows(s).stream().allMatch(row->row.getSelectedAnswer()==null));assertEquals(0,s.getVersion());
    }
    @Test void foreignOptionsAndRepeatedQuestionIdsAreRejected() {
        var s=f.start();var q=f.frozen(s).questions().get(0);
        var invalid=AnswerItemRequest.builder().questionId(q.id()).selectedAnswer(Map.of("type","single_choice","value",new UUID(0,666).toString())).build();
        error("ANSWER_INVALID",()->f.service.save(s.getId(),f.saveRequest(s,List.of(invalid)),USER));
        error("ANSWER_INVALID",()->f.service.save(s.getId(),f.saveRequest(s,List.of(f.correct(q),f.correct(q))),USER));assertEquals(0,s.getVersion());
    }
    @Test void clearingAnAnswerRestoresUnansweredCount() {
        var s=f.start();var q=f.frozen(s).questions().get(0);f.service.save(s.getId(),f.saveRequest(s,List.of(f.correct(q))),USER);
        var clear=AnswerItemRequest.builder().questionId(q.id()).selectedAnswer(new LinkedHashMap<>(Map.of("type","single_choice","value",""))).build();
        f.service.save(s.getId(),f.saveRequest(s,List.of(clear)),USER);assertEquals(0,f.service.progress(s.getId(),USER).getAnsweredQuestions());
    }
    @Test void markingAndCurrentPositionSurviveRepeatedRead() {
        var s=f.start();var q=f.frozen(s).questions().get(0);f.service.mark(s.getId(),q.id(),true,0,USER);
        f.service.navigate(s.getId(),2,1,USER);var detail=f.service.detail(s.getId(),USER);
        assertEquals(List.of(q.id()),detail.getMarkedQuestionIds());assertEquals(2,detail.getCurrentPosition());assertEquals(2,detail.getVersion());
    }
    @Test void sequentialNavigationCannotJumpForward() {
        f.published.rule.setNavigationMode("SEQUENTIAL");f.published.freezeRule();var s=f.start();
        error("NAVIGATION_NOT_ALLOWED",()->f.service.navigate(s.getId(),3,0,USER));assertEquals(1,f.service.detail(s.getId(),USER).getCurrentPosition());
    }
    @Test void noSkipRuleRequiresAnAnswerBeforeNavigationAndManualSubmission() {
        f.published.rule.setAllowSkip(false);f.published.freezeRule();var s=f.start();
        error("SKIP_NOT_ALLOWED",()->f.service.navigate(s.getId(),2,0,USER));error("UNANSWERED_QUESTIONS",()->f.service.submit(s.getId(),"submit-1",USER));
    }
    @Test void pauseAndDuplicateResumeNeverResetStartOrExtendTheTimerTwice() {
        pausing();var s=f.start();var start=s.getStartedAt();f.clock.set(start.plusSeconds(5));var paused=f.service.pause(s.getId(),USER);
        assertEquals(1195,paused.getRemainingSeconds());f.clock.set(start.plusSeconds(15));var resumed=f.service.resume(s.getId(),USER);
        f.clock.set(start.plusSeconds(20));assertEquals(resumed.getExpiredAt(),f.service.resume(s.getId(),USER).getExpiredAt());assertEquals(start,s.getStartedAt());
    }
    @Test void pauseCountLimitAndPausedSaveAreEnforced() {
        pausing();var s=f.start();f.service.pause(s.getId(),USER);
        error("SESSION_CLOSED",()->f.service.save(s.getId(),f.saveRequest(s,List.of(f.correct(f.frozen(s).questions().get(0)))),USER));
        f.service.resume(s.getId(),USER);error("PAUSE_LIMIT",()->f.service.pause(s.getId(),USER));
    }
    @Test void pausedTimeoutIsProcessedEvenWhenTheBrowserHasClosed() {
        pausing();var s=f.start();f.service.pause(s.getId(),USER);f.clock.set(s.getExpiredAt());f.service.processExpired(s.getId());
        assertEquals("EXPIRED",s.getStatus());error("SESSION_NOT_RESUMABLE",()->f.service.resume(s.getId(),USER));
    }
    @Test void autoSubmitGradesSavedSnapshotWhenNoBrowserRequestArrives() {
        f.published.rule.setAutoSubmit(true);f.published.freezeRule();var s=f.start();f.correctAll(s);clearInvocations(f.content);
        f.clock.set(s.getExpiredAt());f.service.processExpired(s.getId());assertEquals("SUBMITTED",s.getStatus());
        assertEquals("PASS",f.service.result(s.getId(),USER).getSummary().getResult());verifyNoInteractions(f.content);
    }
    @Test void expiryWithoutAutoSubmitDoesNotInventASubmittedResult() {
        var s=f.start();f.clock.set(s.getExpiredAt());f.service.processExpired(s.getId());assertEquals("EXPIRED",s.getStatus());
        assertFalse(s.getMetadata().containsKey("gradingSnapshot"));assertFalse(s.getMetadata().containsKey("resultEvent"));
    }
    @Test void gracePeriodAcceptsOnlySubmissionAndNeverAnAnswerChange() {
        f.published.rule.setGracePeriodSeconds(5);f.published.freezeRule();var s=f.start();f.clock.set(s.getExpiredAt().plusSeconds(2));
        error("SESSION_CLOSED",()->f.service.save(s.getId(),f.saveRequest(s,List.of(f.correct(f.frozen(s).questions().get(0)))),USER));
        f.service.submit(s.getId(),"submit-1",USER);assertEquals("LATE_SUBMIT",s.getMetadata().get("submissionReason"));assertEquals("SUBMITTED",s.getStatus());
    }
    @Test void submitRetryReusesTheResultAndStableEventIdentity() {
        var s=f.start();f.correctAll(s);f.service.submit(s.getId(),"submit-1",USER);var event=s.getMetadata().get("resultEvent");int version=s.getVersion();
        var retry=f.service.submit(s.getId(),"submit-1",USER);assertEquals(version,retry.getVersion());assertEquals(event,s.getMetadata().get("resultEvent"));
        error("IDEMPOTENCY_CONFLICT",()->f.service.submit(s.getId(),"submit-2",USER));
    }
    @Test void submitRetryKeepsItsTimestampAfterDatabaseMicrosecondRoundTrip() {
        var session=f.start();f.correctAll(session);
        f.clock.set(f.clock.instant().plusSeconds(3).plusNanos(123456789));
        var first=f.service.submit(session.getId(),"precision-submit",USER);
        var event=session.getMetadata().get("resultEvent");int version=session.getVersion();
        // Model PostgreSQL/JDBC timestamp rounding when the committed row is read by another request.
        Instant persisted=first.getSubmittedAt().plusNanos(500).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        session.setSubmittedAt(persisted);
        var retry=f.service.submit(session.getId(),"precision-submit",USER);
        assertEquals(first.getSubmittedAt(),retry.getSubmittedAt());
        assertEquals(first.getVersion(),retry.getVersion());assertEquals(version,session.getVersion());
        assertEquals("SUBMITTED",retry.getStatus());assertSame(event,session.getMetadata().get("resultEvent"));
    }
    @Test void submitRequiresABoundedIdempotencyKeyAndClosedAttemptsRejectAutosave() {
        var s=f.start();error("IDEMPOTENCY_KEY_REQUIRED",()->f.service.submit(s.getId(),null,USER));
        f.service.submit(s.getId(),"submit-1",USER);error("SESSION_CLOSED",()->f.service.save(s.getId(),f.saveRequest(s,List.of(f.correct(f.frozen(s).questions().get(0)))),USER));
    }
    @Test void gradingWorksDuringContentBuilderFailure() {
        var s=f.start();f.correctAll(s);clearInvocations(f.content);f.service.submit(s.getId(),"submit-1",USER);
        assertEquals("PASS",f.service.result(s.getId(),USER).getSummary().getResult());verifyNoInteractions(f.content);
    }
    @Test void wrongCriticalQuestionFailsEvenAboveThePassingScore() {
        var s=f.start();f.correctAll(s);var q=f.frozen(s).questions().stream().filter(FrozenExamQuestion::isCritical).findFirst().orElseThrow();
        var wrong=q.options().stream().filter(o->!o.isCorrect()).findFirst().orElseThrow();
        f.service.save(s.getId(),f.saveRequest(s,List.of(AnswerItemRequest.builder().questionId(q.id()).selectedAnswer(Map.of("type","single_choice","value",wrong.id().toString())).build())),USER);
        f.service.submit(s.getId(),"submit-1",USER);var summary=f.service.result(s.getId(),USER).getSummary();assertEquals(29,summary.getCorrectQuestions());assertEquals("FAIL",summary.getResult());
    }
    @Test void delayedReleaseHidesScoreReviewHistoryAndKafkaUntilExamEnd() {
        f.published.rule.setResultReleaseMode("AFTER_EXAM_END");f.published.freezeRule();var s=f.start();f.correctAll(s);f.service.submit(s.getId(),"submit-1",USER);
        var pending=f.service.result(s.getId(),USER);assertFalse(pending.isResultPublished());assertNull(pending.getSummary());assertNull(pending.getAnswers());
        assertNull(f.service.historySummary(s));error("RESULT_NOT_PUBLISHED",()->f.service.review(s.getId(),USER));f.service.deliverResult(s.getId());verifyNoInteractions(f.kafka);
        f.clock.set(END);assertTrue(f.service.result(s.getId(),USER).isResultPublished());assertNotNull(f.service.historySummary(s));
    }
    @Test void answerAndExplanationReleaseFlagsAreAppliedIndependently() {
        var s=f.start();f.correctAll(s);f.service.submit(s.getId(),"submit-1",USER);var review=f.service.review(s.getId(),USER);
        assertEquals(EXAM,review.getExamId());assertEquals(VERSION,review.getExamVersionId());
        assertTrue(review.getQuestions().stream().allMatch(q->q.getIsCorrect()==null&&q.getExplanation()==null&&q.getOptions().stream().allMatch(o->o.getIsCorrect()==null)));
        assertTrue(f.service.result(s.getId(),USER).getAnswers().stream().allMatch(a->a.getScore()==null&&a.getIsCorrect()==null));
    }
    @Test void manualReleaseIsIdempotentAndMakesTheStoredEventEligible() {
        f.published.rule.setResultReleaseMode("MANUAL");f.published.freezeRule();var s=f.start();f.service.submit(s.getId(),"submit-1",USER);
        assertFalse(f.service.result(s.getId(),USER).isResultPublished());f.service.deliverResult(s.getId());verifyNoInteractions(f.kafka);
        f.service.releaseManually(s.getId(),new UUID(0,700));int version=s.getVersion();f.service.releaseManually(s.getId(),new UUID(0,700));
        assertEquals(version,s.getVersion());assertTrue(f.service.result(s.getId(),USER).isResultPublished());assertEquals(f.clock.instant().toString(),s.getMetadata().get("outboxNextAttemptAt"));
    }
    @Test void kafkaFailureKeepsPendingEventAndRetryUsesTheSameId() {
        var s=f.start();f.service.submit(s.getId(),"submit-1",USER);var event=s.getMetadata().get("resultEvent");
        when(f.kafka.send(eq("learning-results"),eq(USER.toString()),any(Object.class))).thenReturn(CompletableFuture.failedFuture(new java.io.IOException("Temporary failure")));
        f.service.deliverResult(s.getId());assertEquals("PENDING",s.getMetadata().get("outboxState"));assertEquals(event,s.getMetadata().get("resultEvent"));
        f.clock.set(f.clock.instant().plusSeconds(3));when(f.kafka.send(eq("learning-results"),eq(USER.toString()),any(Object.class))).thenReturn(CompletableFuture.completedFuture(null));
        f.service.deliverResult(s.getId());assertEquals("DELIVERED",s.getMetadata().get("outboxState"));assertEquals(event,s.getMetadata().get("resultEvent"));
        f.service.deliverResult(s.getId());verify(f.kafka,times(2)).send(eq("learning-results"),eq(USER.toString()),any(Object.class));
    }
    @Test void duplicateViolationEventCannotTerminateOrPublishTwice() {
        f.published.rule.setPreventTabSwitch(true);f.published.rule.setMaxTabSwitchCount(0);f.published.freezeRule();var s=f.start();f.correctAll(s);
        var request=new SessionViolationRequest(new UUID(0,999),"TAB_SWITCH");f.service.violation(s.getId(),request,USER);var event=s.getMetadata().get("resultEvent");int version=s.getVersion();
        f.service.violation(s.getId(),request,USER);assertEquals(version,s.getVersion());assertEquals(event,s.getMetadata().get("resultEvent"));
        assertEquals("FAIL",f.service.result(s.getId(),USER).getSummary().getResult());
    }
    @Test void graceStateNeverOffersASecondAttemptThroughStudentReadPolicy() {
        f.published.rule.setAllowRetry(true);f.published.rule.setGracePeriodSeconds(5);f.published.freezeRule();var s=f.start();f.clock.set(s.getExpiredAt().plusSeconds(1));
        var decision=new ExamParticipationPolicy().assess(f.published.version,f.published.reader.read(f.published.version),f.history,f.clock.instant(),true,true);
        assertEquals("SUBMISSION_GRACE",decision.reasonCode());assertFalse(decision.canStart());assertFalse(decision.canResume());assertEquals(s.getId(),decision.activeSessionId());
    }
    @Test void multipleChoiceRequiresTheExactCorrectOptionSet() {
        var s=f.start();f.correctAll(s);var q=multipleQuestion(s);
        String value=q.options().stream().filter(FrozenExamQuestion.Option::isCorrect).map(o->o.id().toString()).collect(java.util.stream.Collectors.joining(","));
        var answer=AnswerItemRequest.builder().questionId(q.id()).selectedAnswer(Map.of("type","multiple_choice","value",value)).build();
        f.service.save(s.getId(),f.saveRequest(s,List.of(answer)),USER);f.service.submit(s.getId(),"submit-1",USER);
        assertEquals(30,f.service.result(s.getId(),USER).getSummary().getCorrectQuestions());
    }
    @Test void partialMultipleChoiceIsWrongAndDuplicateOptionsAreRejected() {
        var s=f.start();f.correctAll(s);var q=multipleQuestion(s);String id=q.options().get(0).id().toString();
        var repeated=AnswerItemRequest.builder().questionId(q.id()).selectedAnswer(Map.of("type","multiple_choice","value",id+","+id)).build();
        error("ANSWER_INVALID",()->f.service.save(s.getId(),f.saveRequest(s,List.of(repeated)),USER));
        var partial=AnswerItemRequest.builder().questionId(q.id()).selectedAnswer(Map.of("type","multiple_choice","value",id)).build();
        f.service.save(s.getId(),f.saveRequest(s,List.of(partial)),USER);f.service.submit(s.getId(),"submit-1",USER);
        assertEquals(29,f.service.result(s.getId(),USER).getSummary().getCorrectQuestions());
    }
    private FrozenExamQuestion multipleQuestion(dts.com.examination.domain.entity.ExamSession session) {
        var paper=f.frozen(session);var original=paper.questions().stream().filter(q->!q.isCritical()).findFirst().orElseThrow();
        var options=new ArrayList<FrozenExamQuestion.Option>();int index=0;
        for(var option:original.options())options.add(new FrozenExamQuestion.Option(option.id(),option.content(),option.sortOrder(),index++<2));
        var question=new FrozenExamQuestion(original.id(),original.contentVersion(),original.content(),"MULTIPLE_CHOICE",false,original.chapterIds(),original.mediaFileIds(),options,original.explanations());
        var questions=new ArrayList<>(paper.questions());questions.set(questions.indexOf(original),question);
        var replacement=new dts.com.examination.application.service.session.FrozenOfficialSession(1,paper.examId(),paper.examVersionId(),paper.examEndedAt(),paper.configuration(),questions,paper.sectionAssignments());
        var metadata=new LinkedHashMap<>(session.getMetadata());metadata.put("officialPaper",f.published.json(replacement));session.setMetadata(metadata);return question;
    }
    private void pausing(){f.published.rule.setAllowPause(true);f.published.rule.setAllowResume(true);f.published.rule.setMaxPauseCount(1);f.published.rule.setMaxPauseDurationSeconds(60);f.published.rule.setResumeTimeoutSeconds(30);f.published.freezeRule();}
    private void error(String code,Runnable operation){var failure=assertThrows(ExaminationException.class,operation::run);assertEquals(code,failure.getCode());}
}
