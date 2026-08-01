package dts.com.examination.api.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.Map;
import java.util.UUID;

@Builder
public record CreateExamRequest(
        @NotBlank(message = "Title must not be blank")
        @Size(max = 100, message = "Title must not exceed 100 characters")
        String title,

        UUID thumbnailId,
        Map<String, Object> metadata
) {
}
