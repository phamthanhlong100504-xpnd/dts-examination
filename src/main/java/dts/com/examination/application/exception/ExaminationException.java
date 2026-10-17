package dts.com.examination.application.exception;

import org.springframework.http.HttpStatus;

import java.util.Objects;

public class ExaminationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ExaminationException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ExaminationException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = Objects.requireNonNull(status, "status");
        if (!status.isError()) {
            throw new IllegalArgumentException("An examination error must have a 4xx or 5xx status");
        }
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw new IllegalArgumentException("A stable examination error code is required");
        }
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}