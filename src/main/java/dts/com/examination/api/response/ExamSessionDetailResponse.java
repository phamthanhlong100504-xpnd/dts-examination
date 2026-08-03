package dts.com.examination.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSessionDetailResponse {
    private UUID sessionId;
    private UUID examVersionId;
    private Integer attemptNo;
    private String status;
    private Instant startedAt;
    private Instant expiredAt;
    private Integer durationSeconds;
    private Long remainingSeconds;
    private Long answeredQuestions;
    private Long totalQuestions;
}
