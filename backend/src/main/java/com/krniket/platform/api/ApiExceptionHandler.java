package com.krniket.platform.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiModels.ApiError> handleStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String code = status.is4xxClientError() ? "REQUEST_REJECTED" : "SERVICE_UNAVAILABLE";
        if (status.is5xxServerError()) {
            String traceId = UUID.randomUUID().toString();
            logger.error("api_error traceId={} status={} path={} code={}", traceId, status.value(),
                    request.getRequestURI(), code, exception);
            return ResponseEntity.status(status).body(new ApiModels.ApiError(code,
                    exception.getReason() == null ? "Request failed." : exception.getReason(),
                    request.getRequestURI(), traceId, Map.of()));
        }
        return error(status, code, exception.getReason() == null ? "Request failed." : exception.getReason(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiModels.ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more fields are invalid.", request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiModels.ApiError> handleConstraint(ConstraintViolationException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "One or more request parameters are invalid.", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiModels.ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        logger.error("api_error traceId={} path={}", traceId, request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ApiModels.ApiError("INTERNAL_ERROR", "An unexpected error occurred.", request.getRequestURI(), traceId, Map.of()));
    }

    private ResponseEntity<ApiModels.ApiError> error(HttpStatus status, String code, String message,
            HttpServletRequest request, Map<String, String> fields) {
        String traceId = UUID.randomUUID().toString();
        logger.warn("api_error traceId={} status={} path={} code={}", traceId, status.value(), request.getRequestURI(), code);
        return ResponseEntity.status(status).body(new ApiModels.ApiError(code, message, request.getRequestURI(), traceId, fields));
    }
}
