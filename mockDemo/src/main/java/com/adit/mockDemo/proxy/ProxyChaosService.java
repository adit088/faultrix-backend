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
 *   1. Extract target = path component of upstream URL
 *   2. Look up chaos rule for this org + target (exact → prefix → regex → default)
 *   3. Run ChaosDecisionEngine (kill switch → enabled → schedule → blast radius → probability)
 *   4. Log the decision (DB + webhooks, async)
 *   5a. If chaos type = ERROR/EXCEPTION → return synthetic error response, skip upstream
 *   5b. If chaos type = LATENCY         → sleep, then forward to upstream
 *   5c. If no chaos                     → forward to upstream immediately
 *   6. Return ProxyResponse with chaos metadata attached
 *
 * This means ANY language (Node, Python, Go, Ruby, etc.) can integrate Faultrix
 * by routing their outbound HTTP through this endpoint. No SDK needed.
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

    /**
     * Main entry point: process one proxied HTTP request.
     */
    public ProxyResponse process(Organization org, ProxyRequest req) {
        String requestId = UUID.randomUUID().toString();
        String target    = extractTarget(req.getUrl());

        log.info("PROXY REQUEST — Org: {}, Method: {}, URL: {}, Target: {}, ReqId: {}",
                org.getSlug(), req.getMethod(), req.getUrl(), target, requestId);

        // ── 1. Resolve chaos rule ────────────────────────────────────────────
        ChaosRule rule = chaosRuleService.getRuleForChaosEngine(org, target);

        // ── 2. Resolve schedules ─────────────────────────────────────────────
        List<ChaosSchedule> schedules = resolveSchedules(org, rule.getTarget());

        // ── 3. Make chaos decision ───────────────────────────────────────────
        ChaosDecision decision = decisionEngine.decide(rule, requestId, schedules);

        // ── 4. Log the decision async (DB + webhooks) ────────────────────────
        eventLogger.logDecision(org, target, decision, requestId);

        // ── 5. Execute based on decision ─────────────────────────────────────
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
            // Latency only — sleep then forward
            injectLatency(decision.getDelayMs());
            return forwardToUpstream(req, decision, target, requestId, decision.getDelayMs());
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

        Map<String, String> responseHeaders = new HashMap<>(result.getHeaders() != null
                ? result.getHeaders()
                : Collections.emptyMap());

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

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Extract the path component of the URL as the "target" for rule matching.
     *
     * Examples:
     *   https://api.stripe.com/v1/charges      → /v1/charges
     *   https://api.example.com/users/123      → /users/123
     *   https://payments.svc/health            → /health
     *
     * This lets chaos rules use patterns like "/v1/charges" or PREFIX "/v1/"
     * to match requests regardless of which upstream host is being called.
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