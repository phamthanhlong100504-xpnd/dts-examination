package dts.com.examination.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record SectionDto(
        @NotBlank(message = "Section code must not be blank")
        @Size(max = 64, message = "Section code must not exceed 64 characters")
        String code,

        @NotBlank(message = "Section title must not be blank")
        @Size(max = 255, message = "Section title must not exceed 255 characters")
        String title,

        @NotNull(message = "Question count is required")
        @Min(value = 1, message = "Question count must be greater than 0")
        Integer questionCount,

        @NotNull(message = "Score is required")
        @Min(value = 0, message = "Score must be at least 0")
        Integer score,

        @NotNull(message = "Order is required")
        @Min(value = 0, message = "Order must be at least 0")
        Integer order,

        List<@NotNull UUID> chapterIds,

        @Min(value = 0, message = "Critical question count must be at least 0")
        Integer criticalQuestionCount
) {
    public SectionDto(String code, String title, Integer questionCount, Integer score, Integer order) {
        this(code, title, questionCount, score, order, List.of(), null);
    }
}
