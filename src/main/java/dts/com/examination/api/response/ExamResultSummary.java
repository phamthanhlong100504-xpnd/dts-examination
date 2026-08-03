package dts.com.examination.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamResultSummary {
    private Integer totalQuestions;
    private Integer answeredQuestions;
    private Integer correctQuestions;
    private Integer wrongQuestions;
    private Integer unansweredQuestions;
    private BigDecimal score;
    private String result;
}
