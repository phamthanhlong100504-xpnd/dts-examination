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
public class InternalQuestionDetailResponse {

    // Existing fields remain compatible with current consumers.
    private UUID id;
    private String content;
    private String type;
    private Boolean isCritical;
    private List<String> mediaFileIds;
    private List<InternalQuestionOptionResponse> options;

    private String status;

    // Revision supplied by Content Builder for snapshot validation.
    private String contentVersion;

    // Source explanations for the private exam-session snapshot.
    // Student response DTOs must enforce the result-release policy.
    private Map<String, Object> explanations;

    // Retain the previous constructor for source compatibility.
    public InternalQuestionDetailResponse(
            UUID id,
            String content,
            String type,
            Boolean isCritical,
            List<String> mediaFileIds,
            List<InternalQuestionOptionResponse> options) {
        this.id = id;
        this.content = content;
        this.type = type;
        this.isCritical = isCritical;
        this.mediaFileIds = mediaFileIds;
        this.options = options;
    }
}
