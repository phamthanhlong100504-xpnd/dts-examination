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
public class SubmitExamResponse {
    private UUID sessionId;
    private String status;
    private Instant submittedAt;
    private ExamResultSummary summary;
}
