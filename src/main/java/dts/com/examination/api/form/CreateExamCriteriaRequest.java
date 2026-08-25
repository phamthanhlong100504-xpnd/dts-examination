package dts.com.examination.api.form;

import dts.com.examination.domain.entity.json.CriteriaConfig;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateExamCriteriaRequest(
        @NotBlank
        @Size(max = 255)
        String title,

        @NotNull
        @Valid
        CriteriaConfig criteria,

        Map<String, Object> metadata
) {}
