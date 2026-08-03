package dts.com.examination.api.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveAnswersRequest {
    @NotEmpty(message = "Answers array cannot be empty")
    @Valid
    private List<AnswerItemRequest> answers;
}
