package dts.com.examination.api.form;

import dts.com.examination.domain.entity.json.CriteriaConfig;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UpdateExamCriteriaRequest(
        @Size(max = 255)
        String title,

        @Valid
        CriteriaConfig criteria,

        Map<String, Object> metadata
) {}
