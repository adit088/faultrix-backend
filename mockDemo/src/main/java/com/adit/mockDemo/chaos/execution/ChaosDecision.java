package com.adit.mockDemo.chaos.execution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a decision about whether and how to inject chaos
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChaosDecision {

    private boolean shouldInjectChaos;

    private ChaosType chaosType;

    private int delayMs;

    private int errorCode;

    private String errorMessage;

    private String target;

    /**
     * Factory method for no chaos decision
     */
    public static ChaosDecision noChaos() {
        return ChaosDecision.builder()
                .shouldInjectChaos(false)
                .chaosType(ChaosType.NONE)
                .build();
    }

    /**
     * Check if this decision involves error injection
     */
    public boolean isError() {
        return chaosType == ChaosType.ERROR_4XX ||
                chaosType == ChaosType.ERROR_5XX ||
                chaosType == ChaosType.TIMEOUT;
    }

    /**
     * Check if this decision involves exception throwing
     */
    public boolean isException() {
        return chaosType == ChaosType.EXCEPTION;
    }

    /**
     * Check if this decision involves latency only
     */
    public boolean isLatencyOnly() {
        return chaosType == ChaosType.LATENCY;
    }
}