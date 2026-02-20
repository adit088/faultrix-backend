package com.adit.mockDemo.dto;

import com.adit.mockDemo.chaos.execution.TargetingMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create or update a chaos rule")
public class ChaosRuleRequest {

    @NotBlank(message = "Target is required")
    @Size(max = 100, message = "Target must not exceed 100 characters")
    @Schema(description = "Target service or endpoint", example = "user-service")
    private String target;

    @Size(max = 200, message = "Target pattern must not exceed 200 characters")
    @Schema(description = "Advanced targeting pattern (regex or prefix)", example = "/api/users.*")
    private String targetPattern;

    @Schema(description = "Targeting mode: EXACT, PREFIX, or REGEX", example = "EXACT")
    private TargetingMode targetingMode;

    @NotNull(message = "Failure rate is required")
    @DecimalMin(value = "0.0", message = "Failure rate must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "Failure rate must be between 0.0 and 1.0")
    @Schema(description = "Probability of chaos injection (0.0 to 1.0)", example = "0.3")
    private Double failureRate;

    @NotNull(message = "Max delay is required")
    @Min(value = 0, message = "Max delay cannot be negative")
    @Max(value = 30000, message = "Max delay cannot exceed 30000ms")
    @Schema(description = "Maximum artificial delay in milliseconds", example = "2000")
    private Long maxDelayMs;

    @NotNull(message = "Enabled status is required")
    @Schema(description = "Whether this rule is active", example = "true")
    private Boolean enabled;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Schema(description = "Human-readable description of the rule", example = "Simulate database timeouts")
    private String description;

    @DecimalMin(value = "0.0", message = "Blast radius must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "Blast radius must be between 0.0 and 1.0")
    @Schema(description = "Percentage of traffic affected (0.0 to 1.0)", example = "1.0")
    private Double blastRadius;

    @Min(value = 0, message = "Seed must be non-negative")
    @Schema(description = "Random seed for deterministic chaos", example = "42")
    private Long seed;

    @Size(max = 200, message = "Tags must not exceed 200 characters")
    @Schema(description = "Comma-separated tags for categorization", example = "database,critical,production")
    private String tags;
}