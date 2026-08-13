package dts.com.examination.application.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningResultEvent {
    private UUID eventId;
    private String eventType;
    private Integer eventVersion;
    private Instant occurredAt;
    private UUID userId;
    private String sourceType;
    private UUID sourceId;
    private String targetType;
    private UUID targetId;
    private Integer attemptNo;
    private String result;
    private BigDecimal score;
    private BigDecimal maxScore;
    private Double progress;
    private Integer durationSeconds;
    private Instant startedAt;
    private Instant completedAt;
    private Map<String, Object> resultSnapshot;
    private Map<String, Object> metadata;
}
