package com.adit.mockDemo.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        log.warn("Resource not found: {} - {}", ex.getResourceType(), ex.getIdentifier());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                error("RESOURCE_NOT_FOUND", ex.getMessage(), 404, request));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex, HttpServletRequest request) {

        log.warn("Business validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                error("VALIDATION_FAILED", ex.getMessage(), 400, request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        log.warn("Bean validation failed on: {}", request.getRequestURI());

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(e -> {
                    FieldError fe = (FieldError) e;
                    return ErrorResponse.FieldError.builder()
                            .field(fe.getField())
                            .message(fe.getDefaultMessage())
                            .rejectedValue(fe.getRejectedValue())
                            .build();
                })
                .collect(Collectors.toList());

        ErrorResponse body = ErrorResponse.builder()
                .errorCode("VALIDATION_FAILED")
                .message("Request validation failed")
                .status(400)
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {

        log.warn("Optimistic locking conflict on: {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                error("CONCURRENT_MODIFICATION",
                        "This resource was modified by another request. Please fetch the latest version and retry.",
                        409, request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                error("INVALID_REQUEST", ex.getMessage(), 400, request));
    }

    @ExceptionHandler(FaultrixApiException.class)
    public ResponseEntity<ErrorResponse> handleNtropiFailure(
            FaultrixApiException ex, HttpServletRequest request) {

        log.error("Ntropi API failure: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                error("NTROPI_API_ERROR", ex.getMessage(), 502, request));
    }

    @ExceptionHandler(ChaosInjectedException.class)
    public ResponseEntity<ErrorResponse> handleChaosInjectedException(
            ChaosInjectedException ex, HttpServletRequest request) {

        log.warn("Chaos injected: {} - {}", ex.getChaosType(), ex.getMessage());

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Chaos-Injected", "true");
        headers.add("X-Chaos-Type", ex.getChaosType().name());

        return ResponseEntity
                .status(ex.getHttpStatus())
                .headers(headers)
                .body(ErrorResponse.builder()
                        .errorCode("CHAOS_INJECTED")
                        .message(ex.getMessage())
                        .status(ex.getHttpStatus())
                        .timestamp(Instant.now())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error on: {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                error("INTERNAL_ERROR",
                        "An unexpected error occurred. Please contact support.",
                        500, request));
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private ErrorResponse error(String code, String message, int status, HttpServletRequest request) {
        return ErrorResponse.builder()
                .errorCode(code)
                .message(message)
                .status(status)
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();
    }
}