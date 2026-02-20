package com.adit.mockDemo.chaos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain model for a chaos rule.
 * Use the Lombok builder for construction — no partial constructors exist
 * to prevent NPE from unintialised fields (target, seed, etc).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChaosRule {

    private String target;
    private Double failureRate;
    private Long   maxDelayMs;
    private Boolean enabled;
    private String description;
    private Long   seed;
    private Double blastRadius;
}