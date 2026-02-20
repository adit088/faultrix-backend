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

        // ── LATENCY: inject delay, then forward ───────────────────────────────
        if (type == ChaosType.LATENCY) {
            int cappedDelay = Math.min(decision.getDelayMs(), UpstreamForwarder.MAX_LATENCY_MS);
            if (cappedDelay != decision.getDelayMs()) {
                log.warn("PROXY LATENCY CAPPED — requested {}ms, capped to {}ms to protect thread pool",
                        decision.getDelayMs(), cappedDelay);
            }
            injectLatency(cappedDelay);
            return forwardToUpstream(req, decision, target, requestId, cappedDelay);
        }

        // ── ERROR / EXCEPTION / PACKET_LOSS / DNS_FAILURE: skip upstream ─────
        if (decision.isError() || decision.isException()) {
            return buildChaosErrorResponse(decision, target, requestId);
        }

        // ── BLACKHOLE: sleep for the full max latency then return 504 ─────────
        if (decision.isBlackhole()) {
            log.info("PROXY BLACKHOLE — Target: {}, ReqId: {} — sleeping {}ms then dropping",
                    target, requestId, UpstreamForwarder.MAX_LATENCY_MS);
            injectLatency(UpstreamForwarder.MAX_LATENCY_MS);
            return buildBlackholeResponse(decision, target, requestId);
        }

        // ── RESOURCE SIMULATION: CPU/MEMORY — simulate, then forward ─────────
        if (decision.isResourceSimulation()) {
            if (type == ChaosType.CPU_SPIKE) {
                injectCpuSpike(Math.min(decision.getDelayMs() > 0 ? decision.getDelayMs() : 500, 3000));
            } else if (type == ChaosType.MEMORY_PRESSURE) {
                injectMemoryPressure();
            }
            return forwardToUpstream(req, decision, target, requestId, 0);
        }

        // ── RESPONSE MUTATION: forward upstream, mutate the response ──────────
        if (decision.isResponseMutation()) {
            ProxyResponse upstream = forwardToUpstream(req, decision, target, requestId, 0);
            return mutateResponse(upstream, decision, target, requestId);
        }

        // Fallback — should not reach here
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

    private ProxyResponse buildBlackholeResponse(ChaosDecision decision,
                                                 String target,
                                                 String requestId) {
        log.info("PROXY BLACKHOLE DROP — Target: {}, ReqId: {}", target, requestId);
        String body = String.format(
                "{\"errorCode\":\"BLACKHOLE\",\"chaosType\":\"BLACKHOLE\"," +
                        "\"message\":\"Request accepted but response dropped (chaos blackhole)\",\"requestId\":\"%s\"}",
                requestId
        );
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Faultrix-Chaos-Injected", "true");
        headers.put("X-Faultrix-Chaos-Type", "BLACKHOLE");
        headers.put("X-Faultrix-Request-Id", requestId);
        headers.put("X-Faultrix-Target", target);

        return ProxyResponse.builder()
                .status(504)  // Gateway Timeout — most accurate semantic for a blackhole
                .body(body)
                .headers(headers)
                .chaosInjected(true)
                .chaosType(decision.getChaosType())
                .injectedDelayMs(UpstreamForwarder.MAX_LATENCY_MS)
                .target(target)
                .requestId(requestId)
                .processedAt(Instant.now())
                .build();
    }

    /**
     * CPU spike: busy-wait loop for the specified duration.
     * This simulates a CPU-saturated upstream that takes longer to respond.
     * Capped at 3000ms to protect the thread pool.
     */
    private void injectCpuSpike(int durationMs) {
        log.debug("PROXY CPU SPIKE — busy-wait {}ms", durationMs);
        long end = System.currentTimeMillis() + durationMs;
        // Busy-wait (intentionally burns CPU — that's the point of this chaos type)
        long sink = 0;
        while (System.currentTimeMillis() < end) {
            sink += System.nanoTime(); // prevents JIT from optimizing the loop away
        }
        log.debug("PROXY CPU SPIKE DONE — sink={}", sink); // use sink so it's not dead code
    }

    /**
     * Memory pressure: allocate a 10MB byte array and hold it for 500ms.
     * This simulates GC pressure / heap exhaustion in an upstream service.
     */
    private void injectMemoryPressure() {
        log.debug("PROXY MEMORY PRESSURE — allocating 10MB");
        try {
            @SuppressWarnings("unused")
            byte[] pressure = new byte[10 * 1024 * 1024]; // 10MB
            java.util.Arrays.fill(pressure, (byte) 42);   // force actual allocation
            Thread.sleep(500);                             // hold it for 500ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Let GC collect it after method returns
        log.debug("PROXY MEMORY PRESSURE DONE");
    }

    /**
     * Response mutation: takes the real upstream response and corrupts it
     * based on the chaos type (CORRUPT_BODY, HEADER_INJECT, BANDWIDTH_LIMIT).
     */
    private ProxyResponse mutateResponse(ProxyResponse upstream,
                                         ChaosDecision decision,
                                         String target,
                                         String requestId) {
        Map<String, String> headers = new HashMap<>(
                upstream.getHeaders() != null ? upstream.getHeaders() : new HashMap<>());
        headers.put("X-Faultrix-Chaos-Injected", "true");
        headers.put("X-Faultrix-Chaos-Type", decision.getChaosType().name());
        headers.put("X-Faultrix-Request-Id", requestId);
        headers.put("X-Faultrix-Target", target);

        String mutatedBody = upstream.getBody();

        switch (decision.getChaosType()) {
            case CORRUPT_BODY -> {
                // Scramble the JSON body — insert junk in the middle
                log.info("PROXY CORRUPT BODY — Target: {}, ReqId: {}", target, requestId);
                String original = upstream.getBody() != null ? upstream.getBody() : "{}";
                // Insert corruption marker at position ~1/3 into the body
                int insertAt = Math.max(1, original.length() / 3);
                mutatedBody = original.substring(0, insertAt)
                        + "<<<FAULTRIX_CORRUPTED_" + requestId.substring(0, 8) + ">>>"
                        + original.substring(insertAt);
            }
            case HEADER_INJECT -> {
                // Inject headers that shouldn't be there — tests header parsing robustness
                log.info("PROXY HEADER INJECT — Target: {}, ReqId: {}", target, requestId);
                headers.put("X-Injected-By-Faultrix", "chaos-engineering");
                headers.put("X-Forwarded-For", "10.0.0.1, 192.168.1.1, 172.16.0.1"); // spoofed chain
                headers.put("X-Real-IP", "0.0.0.0");
                headers.put("Content-Security-Policy", "default-src 'none'"); // may break browser clients
                headers.put("X-Frame-Options", "DENY");
                headers.put("Cache-Control", "no-store, no-cache, must-revalidate, proxy-revalidate");
                headers.put("Retry-After", "3600"); // tell client to wait an hour
            }
            case BANDWIDTH_LIMIT -> {
                // Can't truly throttle bytes in a standard Spring MVC response,
                // but we inject a delay and signal the throttle via headers.
                // Real byte-level throttling would require a WebFlux reactive stream.
                log.info("PROXY BANDWIDTH LIMIT — Target: {}, ReqId: {} — injecting 1500ms simulated throttle",
                        target, requestId);
                injectLatency(1500);
                headers.put("X-Faultrix-Throttled", "true");
                headers.put("X-Faultrix-Simulated-Bandwidth-Kbps", "8"); // 8 kbps — dial-up speed
            }
            default -> {
                // No mutation
            }
        }

        return ProxyResponse.builder()
                .status(upstream.getStatus())
                .body(mutatedBody)
                .headers(headers)
                .chaosInjected(true)
                .chaosType(decision.getChaosType())
                .injectedDelayMs(upstream.getInjectedDelayMs())
                .target(target)
                .requestId(requestId)
                .processedAt(Instant.now())
                .build();
    }

    /**
     * Inject latency by blocking the current thread.
     * Delay is always capped before this is called — safe for the thread pool.
     */
    private void injectLatency(int delayMs) {
        try {
            log.debug("PROXY LATENCY — injecting {}ms", delayMs);
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Proxy latency injection interrupted");
        }
    }
}