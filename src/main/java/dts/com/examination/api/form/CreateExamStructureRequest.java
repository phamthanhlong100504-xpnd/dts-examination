package dts.com.examination.api.form;

import dts.com.examination.application.dto.SectionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record CreateExamStructureRequest(
        @NotBlank(message = "Title must not be blank")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @NotEmpty(message = "Sections must contain at least 1 element")
        @Valid
        List<SectionDto> sections,

        Map<String, Object> metadata
) {
}
