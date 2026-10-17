package dts.com.examination.api.form;

import jakarta.validation.constraints.Size;
import lombok.Builder;
import jakarta.validation.constraints.NotNull;
import dts.com.examination.application.service.selection.ExamSelectionMode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.List;

@Builder
public record UpdateExamVersionRequest(
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        UUID thumbnailId,
        UUID examStructureId,
        UUID examRuleId,
        UUID examCriteriaId,
        Instant startedAt,
        Instant endedAt,
        Map<String, Object> metadata,
        @Size(max = 50) String examType,
        @Size(max = 30) String contentType,
        UUID contentId,
        @Size(max = 10) String licenseClass,
        @Size(max = 128) String bankVersion,
        ExamSelectionMode selectionMode,
        @Size(max = 1000) List<@NotNull UUID> manualQuestionIds
) {
    public UpdateExamVersionRequest(String title, UUID thumbnailId, UUID examStructureId, UUID examRuleId,
            UUID examCriteriaId, Instant startedAt, Instant endedAt, Map<String, Object> metadata) {
        this(title, thumbnailId, examStructureId, examRuleId, examCriteriaId, startedAt, endedAt, metadata,
                null, null, null, null, null, null, null);
    }
}
