package dts.com.examination.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeSessionResponse {
    private UUID sessionId;
    private String status;
    private Instant resumedAt;
    private Instant expiredAt;
    private int remainingSeconds;
}
