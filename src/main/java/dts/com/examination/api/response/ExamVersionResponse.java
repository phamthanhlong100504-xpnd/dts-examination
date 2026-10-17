package dts.com.examination.api.response;

import lombok.Builder;
import dts.com.examination.application.service.selection.ExamSelectionMode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.List;

@Builder
public record ExamVersionResponse(
        UUID id,
        UUID examId,
        Integer versionNo,
        String title,
        String examType,
        UUID thumbnailId,
        UUID examStructureId,
        UUID examRuleId,
        UUID examCriteriaId,
        String contentType,
        UUID contentId,
        Instant startedAt,
        Instant endedAt,
        String status,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt,
        String licenseClass,
        String bankVersion,
        ExamSelectionMode selectionMode,
        List<UUID> manualQuestionIds,
        String matrixPolicyId,
        Integer totalQuestions,
        Integer durationSeconds,
        Integer passingCorrectAnswers,
        Integer criticalQuestionCount
) {
    public ExamVersionResponse(UUID id, UUID examId, Integer versionNo, String title, String examType,
            UUID thumbnailId, UUID examStructureId, UUID examRuleId, UUID examCriteriaId, String contentType,
            UUID contentId, Instant startedAt, Instant endedAt, String status, Map<String, Object> metadata,
            Instant createdAt, Instant updatedAt) {
        this(id, examId, versionNo, title, examType, thumbnailId, examStructureId, examRuleId, examCriteriaId,
                contentType, contentId, startedAt, endedAt, status, metadata, createdAt, updatedAt,
                null, null, null, null, null, null, null, null, null);
    }
}
