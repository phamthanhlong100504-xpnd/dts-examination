package dts.com.examination.application.service;

import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.service.selection.ExamSelectionMode;
import dts.com.examination.application.service.selection.OfficialExamVersionConfig;
import dts.com.examination.domain.entity.*;
import dts.com.examination.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminOfficialVersionPreviewTest {
    private final UUID id = UUID.randomUUID(), sid=UUID.randomUUID(), rid=UUID.randomUUID(), cid=UUID.randomUUID();
    private final ExamVersionRepository versions=mock(ExamVersionRepository.class);
    private final ExamStructureRepository structures=mock(ExamStructureRepository.class);
    private final ExamRuleRepository rules=mock(ExamRuleRepository.class);
    private final ExamCriteriaRepository criteria=mock(ExamCriteriaRepository.class);
    private final OfficialExamVersionValidator validator=mock(OfficialExamVersionValidator.class);
    private final OfficialVersionReviewService reviews=mock(OfficialVersionReviewService.class);
    private final AdminOfficialVersionPreview service=new AdminOfficialVersionPreview(versions,structures,rules,criteria,validator,reviews);

    @Test void publishedVersionCannotBeRevalidatedAsDraft() {
        var v=version("PUBLISHED");when(versions.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(v));
        when(validator.profile(v.getConfigs())).thenReturn(new OfficialExamVersionConfig("B","v1",ExamSelectionMode.AUTO_MATRIX,List.of()));
        var reply=service.validate(id);
        assertFalse(reply.structurallyValid());assertEquals(List.of("VERSION_NOT_DRAFT"),reply.validationErrors());
        verify(validator,never()).validate(any(),any(),any(),any(),any(),anyBoolean());
    }
    @Test void invalidSnapshotReportsExactCodeWithoutSaving() {
        var v=version("DRAFT");setup(v);
        when(validator.validate(eq(v),any(),any(),any(),any(),eq(true)))
                .thenThrow(new ExaminationException(HttpStatus.CONFLICT,"SOURCE_CHANGED","changed"));
        var reply=service.validate(id);
        assertFalse(reply.structurallyValid());assertEquals(List.of("SOURCE_CHANGED"),reply.validationErrors());
        verify(versions,never()).save(any());
    }
    @Test void validatedConfigIsOnlyAPreviewAndNeverAnApproval() {
        var v=version("DRAFT");setup(v);
        when(validator.validate(eq(v),any(),any(),any(),any(),eq(true))).thenReturn(Map.of("officialConfigVersion",1));
        var reply=service.validate(id);
        assertTrue(reply.structurallyValid());assertFalse(reply.approvalBound());assertTrue(reply.approvalGateIntegrated());
        verify(versions,never()).save(any());
    }
    @Test void onlyMatchingApprovedProofIsReportedAsApprovalBound() {
        var v=version("DRAFT");setup(v);
        var valid=Map.<String,Object>of("officialConfigVersion",1);
        when(validator.validate(eq(v),any(),any(),any(),any(),eq(true))).thenReturn(valid);
        when(reviews.approvalMatches(v,valid)).thenReturn(true);
        var reply=service.validate(id);
        assertTrue(reply.structurallyValid());
        assertTrue(reply.approvalBound());
        assertTrue(reply.approvalGateIntegrated());
        verify(versions,never()).save(any());
    }

    private ExamVersion version(String status) {
        return ExamVersion.builder().id(id).status(status).examType("OFFICIAL").examStructureId(sid)
                .examRuleId(rid).examCriteriaId(cid).configs(Map.of("selectionMode","AUTO_MATRIX")).build();
    }
    private void setup(ExamVersion v) {
        when(versions.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(v));
        when(validator.profile(v.getConfigs())).thenReturn(new OfficialExamVersionConfig("B","v1",ExamSelectionMode.AUTO_MATRIX,List.of()));
        when(structures.findById(sid)).thenReturn(Optional.of(ExamStructure.builder().id(sid).build()));
        when(rules.findById(rid)).thenReturn(Optional.of(ExamRule.builder().id(rid).build()));
        when(criteria.findById(cid)).thenReturn(Optional.of(ExamCriteria.builder().id(cid).build()));
    }
}
