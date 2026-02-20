package com.adit.mockDemo.chaos.execution;

/**
 * Types of chaos that can be injected.
 *
 * Original types:
 *   NONE, LATENCY, ERROR_4XX, ERROR_5XX, TIMEOUT, EXCEPTION
 *
 * New types (proxy-level, zero-agent required):
 *   PACKET_LOSS      — drop the response entirely (connection reset simulation)
 *   DNS_FAILURE      — return a DNS NXDOMAIN-style 503 with specific error body
 *   BANDWIDTH_LIMIT  — chunk response with artificial byte-level delays
 *   CORRUPT_BODY     — return garbled/invalid JSON body with 200 status
 *   HEADER_INJECT    — inject bad/unexpected headers into the response
 *   CPU_SPIKE        — busy-wait loop on the server thread before responding
 *   MEMORY_PRESSURE  — allocate and hold a byte array during request processing
 *   BLACKHOLE        — accept connection, wait full timeout, then drop
 */
public enum ChaosType {
    // ── Original types ────────────────────────────────────────────────────────
    NONE,           // No chaos
    LATENCY,        // Add delay only
    ERROR_4XX,      // Return 4xx error
    ERROR_5XX,      // Return 5xx error
    TIMEOUT,        // Simulate timeout (408)
    EXCEPTION,      // Throw exception → 500

    // ── New types (all implementable in HTTP proxy, no agent needed) ──────────
    PACKET_LOSS,    // Drop response — return empty 200 with connection-reset signal
    DNS_FAILURE,    // Simulate DNS resolution failure — 503 + NXDOMAIN error body
    BANDWIDTH_LIMIT,// Slow response: introduce per-chunk delays to simulate throttled pipe
    CORRUPT_BODY,   // Return 200 with deliberately malformed JSON body
    HEADER_INJECT,  // Inject unexpected/malicious headers into the response
    CPU_SPIKE,      // Simulate CPU saturation: busy-wait Nms before forwarding
    MEMORY_PRESSURE,// Simulate memory pressure: allocate heap during request lifecycle
    BLACKHOLE       // Accept connection silently, never respond (hard timeout simulation)
}