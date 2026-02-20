package com.adit.mockDemo.proxy;

import com.adit.mockDemo.chaos.execution.ChaosType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Response returned to the client after chaos decision + optional upstream forwarding.
 *
 * When chaos is NOT injected:
 *   - chaosInjected = false
 *   - status/body/headers reflect the real upstream response
 *
 * When chaos IS injected (error/exception):
 *   - chaosInjected = true
 *   - status = chaos error code (500, 503, 408, etc.)
 *   - body = chaos error message
 *   - upstream is NOT called (fail-fast)
 *
 * When latency chaos is injected:
 *   - chaosInjected = true, chaosType = LATENCY
 *   - upstream IS called, but with an artificial delay applied first
 *   - status/body/headers reflect the real upstream response
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProxyResponse {

    /** HTTP status code (from upstream or from chaos injection) */
    private int status;

    /** Response body (from upstream or chaos error message) */
    private String body;

    /** Response headers forwarded from upstream */
    private Map<String, String> headers;

    /** True if Faultrix injected chaos into this request */
    private boolean chaosInjected;

    /** Which chaos type was injected (null if no chaos) */
    private ChaosType chaosType;

    /** How much latency was injected in ms (0 if none) */
    private int injectedDelayMs;

    /** The target that was matched against chaos rules */
    private String target;

    /** Unique request ID for tracing this call through Faultrix */
    private String requestId;

    /** When this proxy call was processed */
    private Instant processedAt;
}