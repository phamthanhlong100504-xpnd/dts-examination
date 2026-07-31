package dts.com.examination.api.response;

import dts.com.examination.domain.entity.json.CriteriaConfig;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ExamCriteriaResponse(
        UUID id,
        String title,
        String status,
        CriteriaConfig criteria,
        Object metadata
) {}
