package com.adit.mockDemo.chaos.execution;

/**
 * Types of chaos that can be injected
 */
public enum ChaosType {
    NONE,           // No chaos
    LATENCY,        // Add delay only
    ERROR_4XX,      // Return 4xx error
    ERROR_5XX,      // Return 5xx error
    TIMEOUT,        // Simulate timeout
    EXCEPTION       // Throw exception
}