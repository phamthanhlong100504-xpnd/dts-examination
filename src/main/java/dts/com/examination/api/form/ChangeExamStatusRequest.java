package dts.com.examination.api.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record ChangeExamStatusRequest(
        @NotBlank(message = "Status must not be blank")
        String status
) {
}
