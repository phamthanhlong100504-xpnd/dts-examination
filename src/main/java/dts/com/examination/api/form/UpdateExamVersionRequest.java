package dts.com.examination.api.form;

import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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
        Map<String, Object> metadata
) {
}
