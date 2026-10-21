package com.hkv.AiTherapy.exception;

import com.hkv.AiTherapy.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.ErrorResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps controller failures on the documented, content-safe JSON error contract. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> responseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason();
        if (message == null || message.isBlank()) message = defaultMessage(status);
        return error(status, code(status), message);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiResponse<Void>> frameworkError(ErrorResponseException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return error(status, code(status), defaultMessage(status));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> noResource(NoResourceFoundException ignored) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", defaultMessage(HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> invalidBody(MethodArgumentNotValidException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                details.putIfAbsent(error.getField(), safeValidationMessage(error.getDefaultMessage())));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> constraintViolation(ConstraintViolationException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                details.putIfAbsent(violation.getPropertyPath().toString(), safeValidationMessage(violation.getMessage())));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.", details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Void>> invalidRequest(Exception ignored) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request is invalid.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
        log.error("Unhandled API exception type={}", exception.getClass().getSimpleName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String code, String message,
                                                             Map<String, String> details) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message, details));
    }

    public static String code(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST, PAYLOAD_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE -> "VALIDATION_FAILED";
            case UNAUTHORIZED -> "AUTH_REQUIRED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case PRECONDITION_FAILED -> "VERSION_MISMATCH";
            case PRECONDITION_REQUIRED -> "PRECONDITION_REQUIRED";
            case TOO_MANY_REQUESTS -> "RATE_LIMITED";
            case SERVICE_UNAVAILABLE, BAD_GATEWAY, GATEWAY_TIMEOUT -> "PROVIDER_UNAVAILABLE";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_FAILED";
        };
    }

    public static String defaultMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST, PAYLOAD_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE -> "Request is invalid.";
            case UNAUTHORIZED -> "Authentication is required.";
            case FORBIDDEN -> "You are not allowed to perform this action.";
            case NOT_FOUND -> "Resource not found.";
            case CONFLICT -> "Request conflicts with the current resource state.";
            case PRECONDITION_FAILED -> "Resource version does not match.";
            case PRECONDITION_REQUIRED -> "A required precondition is missing.";
            case TOO_MANY_REQUESTS -> "Too many requests. Try again later.";
            case SERVICE_UNAVAILABLE, BAD_GATEWAY, GATEWAY_TIMEOUT -> "Service is temporarily unavailable.";
            default -> status.is5xxServerError() ? "An unexpected error occurred." : "Request failed.";
        };
    }

    private static String safeValidationMessage(String message) {
        if (message == null || message.isBlank()) return "Invalid value.";
        return message.length() > 160 ? message.substring(0, 160) : message;
    }
}
