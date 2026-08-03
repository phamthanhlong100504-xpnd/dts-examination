package dts.com.examination.api.form;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerItemRequest {
    @NotNull(message = "Question ID is required")
    private UUID questionId;
    
    @NotNull(message = "Selected answer is required")
    private Map<String, Object> selectedAnswer;
}
