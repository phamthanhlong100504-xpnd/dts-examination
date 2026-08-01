package dts.com.examination.api.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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

        Map<String, Object> metadata
) {
}
