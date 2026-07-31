package dts.com.examination.api.response;

import dts.com.examination.application.dto.SectionDto;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record ExamStructureResponse(
        UUID id,
        String title,
        String status,
        List<SectionDto> sections,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
