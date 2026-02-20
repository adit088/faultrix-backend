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
     * Check if this decision involves error injection (returns HTTP error, skips upstream)
     */
    public boolean isError() {
        return chaosType == ChaosType.ERROR_4XX ||
                chaosType == ChaosType.ERROR_5XX ||
                chaosType == ChaosType.TIMEOUT ||
                chaosType == ChaosType.DNS_FAILURE ||
                chaosType == ChaosType.PACKET_LOSS;
    }

    /**
     * Check if this decision involves exception throwing
     */
    public boolean isException() {
        return chaosType == ChaosType.EXCEPTION;
    }

    /**
     * Check if this decision involves latency only (still forwards to upstream after delay)
     */
    public boolean isLatencyOnly() {
        return chaosType == ChaosType.LATENCY;
    }

    /**
     * Check if this decision is a proxy-level response mutation
     * (forwards to upstream but mutates the response before returning)
     */
    public boolean isResponseMutation() {
        return chaosType == ChaosType.CORRUPT_BODY ||
                chaosType == ChaosType.HEADER_INJECT ||
                chaosType == ChaosType.BANDWIDTH_LIMIT;
    }

    /**
     * Check if this decision requires server-side resource simulation
     * (runs on the server thread before forwarding)
     */
    public boolean isResourceSimulation() {
        return chaosType == ChaosType.CPU_SPIKE ||
                chaosType == ChaosType.MEMORY_PRESSURE;
    }

    /**
     * Check if this decision is a blackhole (accept + never respond until timeout)
     */
    public boolean isBlackhole() {
        return chaosType == ChaosType.BLACKHOLE;
    }
}