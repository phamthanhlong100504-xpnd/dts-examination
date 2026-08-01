package dts.com.examination.api.response;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
public record ExamVersionResponse(
        UUID id,
        UUID examId,
        Integer versionNo,
        String title,
        String examType,
        UUID thumbnailId,
        UUID examStructureId,
        UUID examRuleId,
        UUID examCriteriaId,
        String contentType,
        UUID contentId,
        Instant startedAt,
        Instant endedAt,
        String status,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
}
