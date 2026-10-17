package dts.com.examination.application.service;

import dts.com.examination.application.service.session.*;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.domain.entity.ExamSession;
import dts.com.examination.domain.entity.json.CriteriaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OfficialSessionGraderTest {
    OfficialSessionFixture f;ExamSession session;FrozenOfficialSession paper;
    final OfficialSessionGrader grader=new OfficialSessionGrader();
    @BeforeEach void setup() throws Exception {f=new OfficialSessionFixture();session=f.start();f.correctAll(session);paper=f.frozen(session);}

    @Test void allCorrectGradesTheRealThirtyQuestionPaperAsPassed() {
        var grade=grade();assertEquals(new BigDecimal("30.00"),grade.score());assertEquals(30,grade.correctCount());assertEquals(30,grade.answeredCount());assertTrue(grade.passed());
    }
    @Test void unansweredCriticalQuestionFailsAbovePassingScore() {
        var id=paper.questions().stream().filter(q->q.isCritical()).findFirst().orElseThrow().id();row(id).setSelectedAnswer(null);
        var grade=grade();assertEquals(29,grade.correctCount());assertTrue(grade.failedCritical());assertFalse(grade.passed());
    }
    @Test void weightedGradingUsesThePublishedSectionWeights() {
        paper.configuration().criteria().getCriteria().setGradingMethod(CriteriaConfig.GradingMethod.WEIGHTED);
        paper.configuration().criteria().getCriteria().setTotalScore(60);
        paper.configuration().criteria().getCriteria().setPassScore(54);
        paper.configuration().structure().getSections().forEach(s->s.setScore(2));
        var grade=grade();assertEquals(new BigDecimal("60.00"),grade.score());assertEquals(new BigDecimal("60"),grade.maxScore());
        assertTrue(grade.questions().stream().allMatch(q->q.score().compareTo(BigDecimal.valueOf(2))==0));
    }
    @Test void percentageGradingNormalizesTheActualWeightedMaximum() {
        var cfg=paper.configuration().criteria().getCriteria();cfg.setGradingMethod(CriteriaConfig.GradingMethod.PERCENTAGE);cfg.setTotalScore(100);cfg.setPassScore(90);
        ordinary(0).setSelectedAnswer(null);var grade=grade();assertEquals(new BigDecimal("96.67"),grade.score());assertEquals(new BigDecimal("100"),grade.maxScore());assertTrue(grade.passed());
    }
    @Test void percentageRoundingUsesConfiguredModeAndPrecision() {
        var cfg=paper.configuration().criteria().getCriteria();cfg.setGradingMethod(CriteriaConfig.GradingMethod.PERCENTAGE);cfg.setTotalScore(100);cfg.setPassScore(90);
        cfg.setRounding(CriteriaConfig.RoundingConfig.builder().mode("HALF_UP").precision(0).build());ordinary(0).setSelectedAnswer(null);
        assertEquals(new BigDecimal("97"),grade().score());
    }
    @Test void wrongAndUnansweredPenaltiesAreAppliedInTheirConfiguredUnits() {
        var cfg=paper.configuration().criteria().getCriteria();cfg.setPenalties(List.of(
                CriteriaConfig.PenaltyConfig.builder().type(CriteriaConfig.PenaltyConfig.PenaltyType.WRONG_ANSWER).deduct(0.5).build(),
                CriteriaConfig.PenaltyConfig.builder().type(CriteriaConfig.PenaltyConfig.PenaltyType.UNANSWERED).deduct(0.25).build()));
        wrong(ordinary(0));ordinary(1).setSelectedAnswer(null);var grade=grade();assertEquals(new BigDecimal("27.25"),grade.score());assertEquals(28,grade.correctCount());assertTrue(grade.passed());
    }
    @Test void mandatoryCorrectRuleOverridesAnOtherwisePassingScore() {
        var row=ordinary(0);wrong(row);mandatory(CriteriaConfig.MandatoryRule.RuleType.MUST_CORRECT,List.of(row.getQuestionId().toString()));assertFalse(grade().passed());
    }
    @Test void mandatoryAttemptRequiresAnAnswerRatherThanCorrectness() {
        var row=ordinary(0);wrong(row);mandatory(CriteriaConfig.MandatoryRule.RuleType.MUST_ATTEMPT,List.of(row.getQuestionId().toString()));assertTrue(grade().passed());
        row.setSelectedAnswer(null);assertFalse(grade().passed());
    }
    @Test void atLeastOneMandatoryRuleChecksAllReferencedQuestions() {
        var first=ordinary(0);var second=ordinary(1);wrong(first);mandatory(CriteriaConfig.MandatoryRule.RuleType.AT_LEAST_ONE,List.of(first.getQuestionId().toString(),second.getQuestionId().toString()));
        assertTrue(grade().passed());wrong(second);assertFalse(grade().passed());
    }
    @Test void sectionMinimumUsesNetPointsAfterPenalties() {
        var row=ordinary(0);var code=paper.sectionAssignments().get(row.getQuestionId());var section=paper.configuration().structure().getSections().stream().filter(s->s.getCode().equals(code)).findFirst().orElseThrow();
        paper.configuration().criteria().getCriteria().setSectionRules(List.of(CriteriaConfig.SectionRule.builder().sectionId(code).minScore(section.getQuestionCount()).build()));
        assertTrue(grade().passed());wrong(row);assertFalse(grade().passed());
    }
    @Test void lowConfiguredPassScoreCannotBypassOfficialCorrectAnswerThreshold() {
        paper.configuration().criteria().getCriteria().setPassScore(0);for(int i=0;i<5;i++)ordinary(i).setSelectedAnswer(null);
        var grade=grade();assertEquals(25,grade.correctCount());assertFalse(grade.failedCritical());assertFalse(grade.passed());
    }
    @Test void proctoringFailureCannotPassEvenWithEveryAnswerCorrect() {assertFalse(grader.grade(paper,f.rows(session),true).passed());}
    @Test void duplicateOrMissingPersistedRowsFailInsteadOfProducingAPartialScore() {
        var rows=new ArrayList<>(f.rows(session));rows.remove(0);assertThrows(ExaminationException.class,()->grader.grade(paper,rows,false));
        rows.add(rows.get(0));assertThrows(ExaminationException.class,()->grader.grade(paper,rows,false));
    }
    @Test void invalidStoredOptionIdsFailBeforeGrading() {ordinary(0).setSelectedAnswer(Map.of("type","single_choice","value",new UUID(0,12345).toString()));assertThrows(ExaminationException.class,this::grade);}
    @Test void unnecessaryRoundingIsRejectedWhenPercentageScoresCannotBeRepresented() {
        var cfg=paper.configuration().criteria().getCriteria();cfg.setGradingMethod(CriteriaConfig.GradingMethod.PERCENTAGE);cfg.setTotalScore(100);cfg.setPassScore(90);
        cfg.setRounding(CriteriaConfig.RoundingConfig.builder().mode("UNNECESSARY").precision(2).build());
        assertThrows(ExaminationException.class,this::grade);
    }
    @Test void unnecessaryIntegerRoundingWorksForExactSumContributions() {
        paper.configuration().criteria().getCriteria().setRounding(CriteriaConfig.RoundingConfig.builder().mode("UNNECESSARY").precision(0).build());
        assertEquals(new BigDecimal("30"),grade().score());
    }
    private OfficialSessionGrader.Grade grade(){return grader.grade(paper,f.rows(session),false);}
    private dts.com.examination.domain.entity.ExamSessionAnswer row(UUID id){return f.rows(session).stream().filter(r->r.getQuestionId().equals(id)).findFirst().orElseThrow();}
    private dts.com.examination.domain.entity.ExamSessionAnswer ordinary(int index){return row(paper.questions().stream().filter(q->!q.isCritical()).toList().get(index).id());}
    private void wrong(dts.com.examination.domain.entity.ExamSessionAnswer row){var q=paper.questions().stream().filter(question->question.id().equals(row.getQuestionId())).findFirst().orElseThrow();row.setSelectedAnswer(Map.of("type","single_choice","value",q.options().stream().filter(o->!o.isCorrect()).findFirst().orElseThrow().id().toString()));}
    private void mandatory(CriteriaConfig.MandatoryRule.RuleType type,List<String> ids){paper.configuration().criteria().getCriteria().setMandatoryRules(List.of(CriteriaConfig.MandatoryRule.builder().type(type).questionIds(ids).build()));}
}
