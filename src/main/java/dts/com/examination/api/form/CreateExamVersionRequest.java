package dts.com.examination.api.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import dts.com.examination.application.service.selection.ExamSelectionMode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.List;

@Builder
public record CreateExamVersionRequest(
        @NotBlank(message = "Title must not be blank")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @NotBlank(message = "Exam type must not be blank")
        @Size(max = 50, message = "Exam type must not exceed 50 characters")
        String examType,

        UUID thumbnailId,

        @NotNull(message = "Exam structure ID must not be null")
        UUID examStructureId,

        @NotNull(message = "Exam rule ID must not be null")
        UUID examRuleId,

        UUID examCriteriaId,

        @NotBlank(message = "Content type must not be blank")
        @Size(max = 30, message = "Content type must not exceed 30 characters")
        String contentType,

        @NotNull(message = "Content ID must not be null")
        UUID contentId,

        Instant startedAt,
        
        Instant endedAt,

        Map<String, Object> metadata,
        @Size(max = 10) String licenseClass,
        @Size(max = 128) String bankVersion,
        ExamSelectionMode selectionMode,
        @Size(max = 1000) List<@NotNull UUID> manualQuestionIds
) {
    public CreateExamVersionRequest(String title, String examType, UUID thumbnailId, UUID examStructureId,
            UUID examRuleId, UUID examCriteriaId, String contentType, UUID contentId, Instant startedAt,
            Instant endedAt, Map<String, Object> metadata) {
        this(title, examType, thumbnailId, examStructureId, examRuleId, examCriteriaId, contentType, contentId,
                startedAt, endedAt, metadata, null, null, null, null);
    }
}
