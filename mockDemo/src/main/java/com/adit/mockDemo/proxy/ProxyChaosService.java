package com.adit.mockDemo.proxy;

import com.adit.mockDemo.chaos.ChaosRule;
import com.adit.mockDemo.chaos.execution.ChaosDecision;
import com.adit.mockDemo.chaos.execution.ChaosDecisionEngine;
import com.adit.mockDemo.chaos.execution.ChaosEventLogger;
import com.adit.mockDemo.chaos.execution.ChaosKillSwitch;
import com.adit.mockDemo.chaos.execution.ChaosType;
import com.adit.mockDemo.entity.ChaosSchedule;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.repository.ChaosRuleRepository;
import com.adit.mockDemo.repository.ChaosScheduleRepository;
import com.adit.mockDemo.service.ChaosRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core service powering the Faultrix HTTP chaos proxy.
 *
 * Flow for every proxied request:
 *   1. SSRF validate upstream URL (SsrfGuard — blocks private IPs, metadata endpoints)
 *   2. Extract target = path component of upstream URL
 *   3. Look up chaos rule for this org + target (exact → prefix → regex → default)
 *   4. Run ChaosDecisionEngine (kill switch → enabled → schedule → blast radius → probability)
 *   5. Log the decision (DB + webhooks, async)
 *   5a. If chaos type = ERROR/EXCEPTION → return synthetic error response, skip upstream
 *   5b. If chaos type = LATENCY         → sleep (capped at MAX_LATENCY_MS), then forward
 *   5c. If no chaos                     → forward immediately
 *   6. Return ProxyResponse with X-Faultrix-* metadata
 *
 * Security:
 *   - SSRF protection via SsrfGuard — validated before any upstream call
 *   - Latency injection capped at UpstreamForwarder.MAX_LATENCY_MS to prevent thread exhaustion
 *   - SsrfException surfaces as 400 Bad Request (not 500) so the error is clear to the caller
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProxyChaosService {

    private final ChaosRuleService        chaosRuleService;
    private final ChaosDecisionEngine     decisionEngine;
    private final ChaosEventLogger        eventLogger;
    private final ChaosKillSwitch         killSwitch;
    private final ChaosRuleRepository     chaosRuleRepository;
    private final ChaosScheduleRepository scheduleRepository;
    private final UpstreamForwarder       upstreamForwarder;
    private final SsrfGuard               ssrfGuard;

    /**
     * Main entry point: process one proxied HTTP request.
     */
    public ProxyResponse process(Organization org, ProxyRequest req) {
        String requestId = UUID.randomUUID().toString();

        // ── 0. SSRF validation — before anything else ────────────────────────
        // Return 400 immediately if the URL is blocked — don't log a chaos event
        try {
            ssrfGuard.validate(req.getUrl());
        } catch (SsrfGuard.SsrfException e) {
            log.warn("PROXY SSRF BLOCKED — Org: {}, URL: {}, Reason: {}, ReqId: {}",
                    org.getSlug(), req.getUrl(), e.getMessage(), requestId);
            return buildSsrfBlockedResponse(e.getMessage(), req.getUrl(), requestId);
        }

        String target = extractTarget(req.getUrl());

        log.info("PROXY REQUEST — Org: {}, Method: {}, URL: {}, Target: {}, ReqId: {}",
                org.getSlug(), req.getMethod(), req.getUrl(), target, requestId);

        // ── 1. Resolve chaos rule ─────────────────────────────────────────────
        ChaosRule rule = chaosRuleService.getRuleForChaosEngine(org, target);

        // ── 2. Resolve schedules ──────────────────────────────────────────────
        List<ChaosSchedule> schedules = resolveSchedules(org, rule.getTarget());

        // ── 3. Make chaos decision ────────────────────────────────────────────
        ChaosDecision decision = decisionEngine.decide(rule, requestId, schedules);

        // ── 4. Log the decision async (DB + webhooks) ─────────────────────────
        eventLogger.logDecision(org, target, decision, requestId);

        // ── 5. Execute based on decision ──────────────────────────────────────
        return executeDecision(req, decision, target, requestId);
    }

    // ── Decision Execution ───────────────────────────────────────────────────

    private ProxyResponse executeDecision(ProxyRequest req,
                                          ChaosDecision decision,
                                          String target,
                                          String requestId) {

        if (!decision.isShouldInjectChaos()) {
            // No chaos — forward immediately
            return forwardToUpstream(req, decision, target, requestId, 0);
        }

        ChaosType type = decision.getChaosType();

        if (type == ChaosType.LATENCY) {
            // Latency injection — cap at MAX_LATENCY_MS to prevent thread exhaustion
            int cappedDelay = Math.min(decision.getDelayMs(), UpstreamForwarder.MAX_LATENCY_MS);
            if (cappedDelay != decision.getDelayMs()) {
                log.warn("PROXY LATENCY CAPPED — requested {}ms, capped to {}ms to protect thread pool",
                        decision.getDelayMs(), cappedDelay);
            }
            injectLatency(cappedDelay);
            return forwardToUpstream(req, decision, target, requestId, cappedDelay);
        }

        if (decision.isError() || decision.isException()) {
            // Error/exception — return synthetic response, skip upstream entirely
            return buildChaosErrorResponse(decision, target, requestId);
        }

        // Fallback — should not reach here, but forward safely if it does
        return forwardToUpstream(req, decision, target, requestId, 0);
    }

    private ProxyResponse forwardToUpstream(ProxyRequest req,
                                            ChaosDecision decision,
                                            String target,
                                            String requestId,
                                            int injectedDelayMs) {
        UpstreamResult result = upstreamForwarder.forward(
                req.getMethod(),
                req.getUrl(),
                req.getHeaders(),
                req.getBody()
        );

        Map<String, String> responseHeaders = new HashMap<>(
                result.getHeaders() != null ? result.getHeaders() : Collections.emptyMap());

        // Always add Faultrix tracing headers
        responseHeaders.put("X-Faultrix-Request-Id", requestId);
        responseHeaders.put("X-Faultrix-Target", target);
        if (decision.isShouldInjectChaos()) {
            responseHeaders.put("X-Faultrix-Chaos-Injected", "true");
            responseHeaders.put("X-Faultrix-Chaos-Type", decision.getChaosType().name());
            if (injectedDelayMs > 0) {
                responseHeaders.put("X-Faultrix-Delay-Ms", String.valueOf(injectedDelayMs));
            }
        } else {
            responseHeaders.put("X-Faultrix-Chaos-Injected", "false");
        }

        return ProxyResponse.builder()
                .status(result.getStatus())
                .body(result.getBody())
                .headers(responseHeaders)
                .chaosInjected(decision.isShouldInjectChaos())
                .chaosType(decision.isShouldInjectChaos() ? decision.getChaosType() : null)
                .injectedDelayMs(injectedDelayMs)
                .target(target)
                .requestId(requestId)
                .processedAt(Instant.now())
                .build();
    }

    private ProxyResponse buildChaosErrorResponse(ChaosDecision decision,
                                                  String target,
                                                  String requestId) {
        log.info("PROXY CHAOS ERROR — Target: {}, Type: {}, Status: {}, ReqId: {}",
                target, decision.getChaosType(), decision.getErrorCode(), requestId);

        String body = String.format(
                "{\"errorCode\":\"CHAOS_INJECTED\",\"chaosType\":\"%s\",\"message\":\"%s\",\"requestId\":\"%s\"}",
                decision.getChaosType().name(),
                decision.getErrorMessage() != null ? decision.getErrorMessage() : "Chaos injected by Faultrix",
                requestId
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Faultrix-Chaos-Injected", "true");
        headers.put("X-Faultrix-Chaos-Type", decision.getChaosType().name());
        headers.put("X-Faultrix-Request-Id", requestId);
        headers.put("X-Faultrix-Target", target);

        return ProxyResponse.builder()
                .status(decision.getErrorCode())
                .body(body)
                .headers(headers)
                .chaosInjected(true)
                .chaosType(decision.getChaosType())
                .injectedDelayMs(0)
                .target(target)
                .requestId(requestId)
                .processedAt(Instant.now())
                .build();
    }

    private ProxyResponse buildSsrfBlockedResponse(String reason, String url, String requestId) {
        String body = String.format(
                "{\"errorCode\":\"SSRF_BLOCKED\",\"message\":\"Request blocked: %s\",\"requestId\":\"%s\"}",
                reason.replace("\"", "'"),
                requestId
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Faultrix-Request-Id", requestId);
        headers.put("X-Faultrix-Blocked", "SSRF");

        return ProxyResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())   // 400 — clear signal to the caller
                .body(body)
                .headers(headers)
                .chaosInjected(false)
                .chaosType(null)
                .injectedDelayMs(0)
                .target(url)
                .requestId(requestId)
                .processedAt(Instant.now())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extract the path component of the URL as the "target" for rule matching.
     *
     * Examples:
     *   https://api.stripe.com/v1/charges      → /v1/charges
     *   https://api.example.com/users/123      → /users/123
     *   https://payments.svc/health            → /health
     */
    private String extractTarget(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            return (path == null || path.isBlank()) ? "/" : path;
        } catch (Exception e) {
            log.warn("Could not parse URL '{}' — using full URL as target", url);
            return url;
        }
    }

    private List<ChaosSchedule> resolveSchedules(Organization org, String target) {
        try {
            return chaosRuleRepository
                    .findByOrganizationAndTarget(org, target)
                    .map(scheduleRepository::findByChaosRuleAndEnabledTrue)
                    .orElse(Collections.emptyList());
        } catch (Exception e) {
            log.warn("Could not resolve schedules for target: {} — defaulting to always active", target);
            return Collections.emptyList();
        }
    }

    /**
     * Inject latency by blocking the current thread.
     *
     * This is safe because:
     *  - Delay is capped at MAX_LATENCY_MS (8s) in executeDecision above
     *  - The proxy endpoint has its own rate limit (same as all endpoints)
     *  - Railway provides enough threads for reasonable concurrent load
     *
     * A full async/reactive approach would be better at very high scale,
     * but requires migrating to WebFlux — not appropriate for this Spring MVC app.
     */
    private void injectLatency(int delayMs) {
        if (delayMs <= 0) return;
        try {
            log.debug("PROXY LATENCY — injecting {}ms", delayMs);
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Proxy latency injection interrupted");
        }
    }
}