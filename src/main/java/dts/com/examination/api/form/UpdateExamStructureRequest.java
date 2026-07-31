package dts.com.examination.api.form;

import dts.com.examination.application.dto.SectionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record UpdateExamStructureRequest(
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Valid
        List<SectionDto> sections,

        String status,

        Map<String, Object> metadata
) {
}
