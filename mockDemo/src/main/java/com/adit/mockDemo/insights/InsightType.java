package com.adit.mockDemo.insights;

public enum InsightType {
    // ── Failure Patterns ──────────────────────────────────────────────────────
    CASCADING_FAILURE,           // Downstream amplification
    FAILURE_OVERRUN,             // More failures than expected
    CIRCUIT_BREAKER_MISSING,     // High 5xx rate without protection
    RETRY_STORM,                 // Aggressive retries causing issues

    // ── Latency Patterns ──────────────────────────────────────────────────────
    LATENCY_AMPLIFICATION,       // Slowness causing failures
    TIMEOUT_MISSING,             // Long delays without timeouts
    SLOW_RECOVERY,               // Takes too long to recover

    // ── Traffic Patterns ──────────────────────────────────────────────────────
    LOW_SAMPLE_BIAS,             // Not enough traffic for confidence
    PEAK_HOUR_FRAGILITY,         // Fails more during high traffic
    BLAST_RADIUS_MISMATCH,       // Config vs actual blast radius

    // ── Resource Patterns ─────────────────────────────────────────────────────
    RESOURCE_EXHAUSTION,         // Connections/memory running out
    QUEUE_BUILDUP,               // Work piling up
    RATE_LIMIT_HIT,              // Hitting rate limits

    // ── Resilience Gaps ───────────────────────────────────────────────────────
    MISSING_FALLBACK,            // No degraded mode
    CACHE_POISONING,             // Caching errors
    DEPENDENCY_HELL,             // Too many downstream deps

    // ── Good News ─────────────────────────────────────────────────────────────
    RESILIENT_SYSTEM,            // System handled chaos well
    OPTIMAL_RECOVERY,            // Fast recovery observed
    GOOD_ISOLATION               // Failures properly contained
}