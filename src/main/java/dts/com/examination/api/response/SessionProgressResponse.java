package dts.com.examination.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionProgressResponse {
    private long answeredQuestions;
    private long totalQuestions;
    private int remainingSeconds;
    private Integer version;
    private Integer currentPosition;
    private String status;
    private java.time.Instant serverNow;
    private java.util.List<java.util.UUID> markedQuestionIds;
}
