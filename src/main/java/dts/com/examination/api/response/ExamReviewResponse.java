package dts.com.examination.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamReviewResponse {
    private UUID sessionId;
    private UUID examId;
    private UUID examVersionId;
    private String finalResult;
    private BigDecimal score;
    private Integer correctCount;
    private Integer totalQuestions;
    private List<QuestionReviewResponse> questions;
}
