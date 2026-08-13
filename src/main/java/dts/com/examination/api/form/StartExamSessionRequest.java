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
public class StartExamSessionRequest {
    
    private UUID examVersionId;
    
    private UUID examId;
    
    private Map<String, Object> clientInfo;
}
