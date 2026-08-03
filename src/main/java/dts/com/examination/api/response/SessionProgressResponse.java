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
}
