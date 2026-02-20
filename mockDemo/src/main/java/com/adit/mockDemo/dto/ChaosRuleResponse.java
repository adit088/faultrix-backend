package com.adit.mockDemo.dto;

import com.adit.mockDemo.chaos.execution.TargetingMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chaos rule configuration")
public class ChaosRuleResponse {

    @Schema(description = "Unique identifier", example = "1")
    private Long id;

    @Schema(description = "Organization ID this rule belongs to", example = "1")
    private Long organizationId;

    @Schema(description = "Target service or endpoint", example = "user-service")
    private String target;

    @Schema(description = "Advanced targeting pattern (regex or prefix)", example = "/api/users.*")
    private String targetPattern;

    @Schema(description = "Targeting mode: EXACT, PREFIX, or REGEX", example = "EXACT")
    private TargetingMode targetingMode;

    @Schema(description = "Probability of chaos injection (0.0 to 1.0)", example = "0.3")
    private Double failureRate;

    @Schema(description = "Maximum artificial delay in milliseconds", example = "2000")
    private Long maxDelayMs;

    @Schema(description = "Whether this rule is active", example = "true")
    private Boolean enabled;

    @Schema(description = "Human-readable description", example = "Simulate database timeouts")
    private String description;

    @Schema(description = "Percentage of traffic affected", example = "1.0")
    private Double blastRadius;

    @Schema(description = "Random seed for deterministic chaos", example = "42")
    private Long seed;

    @Schema(description = "Comma-separated tags", example = "database,critical,production")
    private String tags;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    @Schema(description = "User who created the rule")
    private String createdBy;

    @Schema(description = "User who last updated the rule")
    private String updatedBy;

    @Schema(description = "Optimistic locking version")
    private Long version;
}