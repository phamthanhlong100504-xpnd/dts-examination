package dts.com.examination.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionReviewResponse {
    private UUID id;
    private String content;
    private Integer sortOrder;
    private Boolean isCorrect;
}
