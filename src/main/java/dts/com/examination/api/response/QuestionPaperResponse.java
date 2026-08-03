package dts.com.examination.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionPaperResponse {
    private UUID questionId;
    private Map<String, Object> display;
    private String content;
    private String type;
    private List<OptionPaperResponse> options;
    private Map<String, Object> selectedAnswer;
}
