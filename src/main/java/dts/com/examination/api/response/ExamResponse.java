package dts.com.examination.api.response;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
public record ExamResponse(
        UUID id,
        String title,
        UUID thumbnailId,
        String status,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
}
