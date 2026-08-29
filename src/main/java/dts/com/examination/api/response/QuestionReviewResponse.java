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
public class QuestionReviewResponse {
    private UUID id;
    private String content;
    private String type;
    private List<String> mediaFileIds;
    private List<OptionReviewResponse> options;
    
    private Map<String, Object> userAnswer;
    private Boolean isCorrect;
    private String explanation; // If explanation exists later, can be added here
}
