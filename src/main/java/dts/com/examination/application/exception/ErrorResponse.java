package dts.com.examination.application.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private String traceId;
    private String code;

    public ErrorResponse(int status, String error, String message, String traceId) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.traceId = traceId;
    }
}