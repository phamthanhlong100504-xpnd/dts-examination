package dts.com.examination.application.service;

import dts.com.examination.api.form.UpdateExamCriteriaRequest;
import dts.com.examination.api.form.UpdateExamRuleRequest;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.domain.entity.ExamCriteria;
import dts.com.examination.domain.entity.ExamRule;
import dts.com.examination.domain.entity.json.CriteriaConfig;
import dts.com.examination.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExamConfigurationReferenceGuardTest {
    private static final UUID ID=new UUID(0,1);
    private ExamVersionRepository versions;
    private ExamRuleRepository rules;
    private ExamCriteriaRepository criterias;
    private ExamRuleService ruleService;
    private ExamCriteriaService criteriaService;
    private ExamRule rule;
    private ExamCriteria criteria;

    @BeforeEach
    void setUp() {
        versions=mock(ExamVersionRepository.class);rules=mock(ExamRuleRepository.class);criterias=mock(ExamCriteriaRepository.class);
        ruleService=new ExamRuleService(versions,rules);criteriaService=new ExamCriteriaService(versions,criterias);
        rule=ExamRule.builder().id(ID).title("Existing rule").status("ACTIVE").build();
        criteria=ExamCriteria.builder().id(ID).title("Existing criteria").criteria(CriteriaConfig.builder().totalScore(30).passScore(27)
                .gradingMethod(CriteriaConfig.GradingMethod.SUM).build()).build();
        when(rules.findByIdForUpdate(ID)).thenReturn(Optional.of(rule));
        when(criterias.findByIdForUpdate(ID)).thenReturn(Optional.of(criteria));
        when(rules.save(any())).thenAnswer(call->call.getArgument(0));
        when(criterias.save(any())).thenAnswer(call->call.getArgument(0));
    }

    @Test
    void publishedOrArchivedRuleCannotBeUpdated() {
        when(versions.existsByExamRuleIdAndStatusIn(eq(ID),anyCollection())).thenReturn(true);
        error("RULE_IN_USE",()->ruleService.updateRule(ID,new UpdateExamRuleRequest()));
        verify(rules,never()).save(any());verify(versions).existsByExamRuleIdAndStatusIn(ID,List.of("PUBLISHED","ARCHIVED"));
    }

    @Test
    void anyVersionReferencePreventsRuleDeletionIncludingHistoricalReferences() {
        when(versions.existsByExamRuleId(ID)).thenReturn(true);
        error("RULE_REFERENCED",()->ruleService.deleteRule(ID));assertNull(rule.getDeletedAt());verify(rules,never()).save(any());
    }

    @Test
    void publishedOrArchivedCriteriaCannotBeUpdated() {
        when(versions.existsByExamCriteriaIdAndStatusIn(eq(ID),anyCollection())).thenReturn(true);
        error("CRITERIA_IN_USE",()->criteriaService.updateCriteria(ID,new UpdateExamCriteriaRequest("Changed",null,null)));
        assertEquals("Existing criteria",criteria.getTitle());verify(criterias,never()).save(any());
        verify(versions).existsByExamCriteriaIdAndStatusIn(ID,List.of("PUBLISHED","ARCHIVED"));
    }

    @Test
    void anyVersionReferencePreventsCriteriaDeletionIncludingHistoricalReferences() {
        when(versions.existsByExamCriteriaId(ID)).thenReturn(true);
        error("CRITERIA_REFERENCED",()->criteriaService.deleteCriteria(ID));assertNull(criteria.getDeletedAt());verify(criterias,never()).save(any());
    }

    @Test
    void unreferencedRuleCanStillBeUpdatedAndSoftDeletedUsingTheSameRowLock() {
        assertNotNull(ruleService.updateRule(ID,new UpdateExamRuleRequest()));
        ruleService.deleteRule(ID);assertNotNull(rule.getDeletedAt());verify(rules,times(2)).findByIdForUpdate(ID);
    }

    @Test
    void unreferencedCriteriaCanStillBeUpdatedAndSoftDeletedUsingTheSameRowLock() {
        assertNotNull(criteriaService.updateCriteria(ID,new UpdateExamCriteriaRequest("Changed",null,null)));
        criteriaService.deleteCriteria(ID);assertNotNull(criteria.getDeletedAt());verify(criterias,times(2)).findByIdForUpdate(ID);
    }

    private void error(String code,Runnable action) {
        var error=assertThrows(ExaminationException.class,action::run);assertEquals(code,error.getCode());assertEquals(409,error.getStatus().value());
    }
}
