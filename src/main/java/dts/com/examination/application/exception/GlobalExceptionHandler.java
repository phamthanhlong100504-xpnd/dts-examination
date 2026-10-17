package dts.com.examination.application.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ExaminationException.class)
    public ResponseEntity<ErrorResponse> handleExaminationException(
            ExaminationException ex, HttpServletRequest request, HttpServletResponse servletResponse) {
        log.warn("Examination error: code={}, status={}", ex.getCode(), ex.getStatus().value());
        return error(ex.getStatus(), ex.getCode(), ex.getMessage(), request, servletResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request, HttpServletResponse servletResponse) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(validation -> validation.getDefaultMessage())
                .filter(value -> value != null && !value.isBlank())
                .findFirst().orElse("Invalid request");
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Validation failed: " + message, request, servletResponse);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidationException(
            HandlerMethodValidationException ex, HttpServletRequest request, HttpServletResponse servletResponse) {
        if (ex.isForReturnValue()) {
            log.error("Response validation failed", ex);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                    "An unexpected error occurred. Please contact support.", request, servletResponse);
        }
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid request parameters", request, servletResponse);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, ConstraintViolationException.class,
            BindException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(
            Exception ex, HttpServletRequest request, HttpServletResponse servletResponse) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                "Invalid request parameters or JSON body", request, servletResponse);
    }

    @ExceptionHandler(BusinessValidationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessValidationException(
            BusinessValidationException ex, HttpServletRequest request, HttpServletResponse servletResponse) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request, servletResponse);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request, HttpServletResponse servletResponse) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request, servletResponse);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request, HttpServletResponse servletResponse) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request, servletResponse);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpServletRequest request, HttpServletResponse servletResponse) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request, servletResponse);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRuleException(
            BusinessRuleException ex, HttpServletRequest request, HttpServletResponse servletResponse) {
        return error(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), request, servletResponse);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request, HttpServletResponse servletResponse) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to access this resource", request, servletResponse);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request, HttpServletResponse servletResponse) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "A valid access token is required", request, servletResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request, HttpServletResponse servletResponse) {
        log.error("Unexpected server error occurred", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please contact support.", request, servletResponse);
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status, String code, String message, HttpServletRequest request, HttpServletResponse servletResponse) {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader("X-Request-ID");
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .traceId(traceId)
                .code(code)
                .build();

        // Replace the filter's header instead of appending a second value.
        servletResponse.setHeader("X-Request-ID", traceId);

        ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (status == HttpStatus.UNAUTHORIZED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return response.body(body);
    }
}