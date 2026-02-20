package com.adit.mockDemo.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response format")
public class ErrorResponse {

    @Schema(description = "Machine-readable error code", example = "VALIDATION_FAILED")
    private String errorCode;

    @Schema(description = "Human-readable error message", example = "Request validation failed")
    private String message;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Timestamp when error occurred")
    private Instant timestamp;

    @Schema(description = "Request path that caused the error", example = "/chaos/rules")
    private String path;

    @Schema(description = "Field-level validation errors")
    private List<FieldError> fieldErrors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Field-level validation error")
    public static class FieldError {

        @Schema(description = "Field name", example = "failureRate")
        private String field;

        @Schema(description = "Validation error message", example = "must be between 0.0 and 1.0")
        private String message;

        @Schema(description = "Rejected value")
        private Object rejectedValue;
    }
}