package dts.com.examination.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionHistoryItemResponse {
    private UUID sessionId;
    private UUID examVersionId;
    private Integer attemptNo;
    private String status;
    private Instant startedAt;
    private Instant submittedAt;
    private ExamResultSummary summary;
}
