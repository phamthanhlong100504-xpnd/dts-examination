package dts.com.examination.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record SectionDto(
        @NotBlank(message = "Section code must not be blank")
        String code,

        @NotBlank(message = "Section title must not be blank")
        String title,

        @NotNull(message = "Question count is required")
        @Min(value = 1, message = "Question count must be greater than 0")
        Integer questionCount,

        @NotNull(message = "Score is required")
        @Min(value = 0, message = "Score must be at least 0")
        Integer score,

        @NotNull(message = "Order is required")
        Integer order
) {
}
