package com.adit.mockDemo.chaos.execution;

import com.adit.mockDemo.chaos.ChaosRule;
import com.adit.mockDemo.entity.ChaosSchedule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core decision engine for chaos injection.
 * Order of checks: KillSwitch → Rule enabled → Schedule → BlastRadius → Probability
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChaosDecisionEngine {

    private final ChaosKillSwitch   killSwitch;
    private final ScheduleEvaluator scheduleEvaluator;

    /**
     * @param rule      The chaos rule to evaluate
     * @param requestId Unique identifier for determinism
     * @param schedules Active schedules for this rule (empty = always active)
     */
    public ChaosDecision decide(ChaosRule rule,
                                String requestId,
                                List<ChaosSchedule> schedules) {

        // 1. Global kill switch
        if (!killSwitch.isChaosEnabled()) {
            log.trace("Chaos globally disabled via kill switch");
            return ChaosDecision.noChaos();
        }

        // 2. Rule enabled flag
        if (!rule.getEnabled()) {
            log.trace("Chaos rule disabled for target: {}", rule.getTarget());
            return ChaosDecision.noChaos();
        }

        // 3. Schedule window
        if (!scheduleEvaluator.isActiveNow(schedules)) {
            log.debug("Chaos rule '{}' outside schedule window — skipping", rule.getTarget());
            return ChaosDecision.noChaos();
        }

        // 4. Blast radius
        if (!shouldAffectRequest(rule.getBlastRadius(), requestId)) {
            log.trace("Request outside blast radius for target: {}", rule.getTarget());
            return ChaosDecision.noChaos();
        }

        // 5. Probability roll
        double roll = getRandomDouble(rule.getSeed(), requestId);

        if (roll < rule.getFailureRate()) {
            double typeRoll = getRandomDouble(rule.getSeed(), requestId + "_type");
            double codeRoll = getRandomDouble(rule.getSeed(), requestId + "_code");

            ChaosType type      = determineFailureType(typeRoll);
            int       errorCode = determineErrorCode(type, codeRoll);
            int       delayMs   = resolveDelayMs(rule, requestId + "_delay");

            log.debug("CHAOS INJECTED - Target: {}, Type: {}, FailureRate: {}, Roll: {}",
                    rule.getTarget(), type, rule.getFailureRate(), roll);

            return ChaosDecision.builder()
                    .shouldInjectChaos(true)
                    .chaosType(type)
                    .delayMs(delayMs)
                    .errorCode(errorCode)
                    .errorMessage(generateErrorMessage(type))
                    .target(rule.getTarget())
                    .build();
        }

        // 6. Latency-only path
        if (rule.getMaxDelayMs() != null && rule.getMaxDelayMs() > 0
                && roll >= rule.getFailureRate() && roll < 0.5) {

            int delay = resolveDelayMs(rule, requestId + "_latency");
            log.debug("LATENCY INJECTED - Target: {}, Delay: {}ms", rule.getTarget(), delay);

            return ChaosDecision.builder()
                    .shouldInjectChaos(true)
                    .chaosType(ChaosType.LATENCY)
                    .delayMs(delay)
                    .target(rule.getTarget())
                    .build();
        }

        return ChaosDecision.noChaos();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private int resolveDelayMs(ChaosRule rule, String seed) {
        if (rule.getMaxDelayMs() == null || rule.getMaxDelayMs() <= 0) return 0;
        long max = rule.getMaxDelayMs();
        if (max <= 50) return (int) max;
        long range = max - 50;
        long raw   = Math.abs(deriveHash(rule.getSeed(), seed) % range);
        return (int) (50 + raw);
    }

    private boolean shouldAffectRequest(Double blastRadius, String requestId) {
        if (blastRadius == null || blastRadius >= 1.0) return true;
        if (blastRadius <= 0.0) return false;
        int    hash     = Math.abs(requestId.hashCode());
        double position = (hash % 10000) / 10000.0;
        return position < blastRadius;
    }

    private double getRandomDouble(Long seed, String identifier) {
        if (seed == null) return ThreadLocalRandom.current().nextDouble();
        long hash = deriveHash(seed, identifier);
        return (Math.abs(hash) % 100_000) / 100_000.0;
    }

    private long deriveHash(Long seed, String identifier) {
        long base = seed != null ? seed : 0L;
        return base * 31L + identifier.hashCode();
    }

    private ChaosType determineFailureType(double roll) {
        if (roll < 0.3) return ChaosType.ERROR_5XX;
        if (roll < 0.6) return ChaosType.ERROR_4XX;
        if (roll < 0.8) return ChaosType.TIMEOUT;
        return ChaosType.EXCEPTION;
    }

    private int determineErrorCode(ChaosType type, double roll) {
        return switch (type) {
            case ERROR_5XX -> { int[] c = {500,502,503,504}; yield c[(int)(roll*c.length)%c.length]; }
            case ERROR_4XX -> { int[] c = {400,404,429,408}; yield c[(int)(roll*c.length)%c.length]; }
            case TIMEOUT   -> 408;
            default        -> 500;
        };
    }

    private String generateErrorMessage(ChaosType type) {
        return switch (type) {
            case ERROR_5XX -> "Internal Server Error - Chaos Engineering Simulation";
            case ERROR_4XX -> "Bad Request - Chaos Engineering Simulation";
            case TIMEOUT   -> "Request Timeout - Chaos Engineering Simulation";
            case EXCEPTION -> "Service Unavailable - Chaos Engineering Simulation";
            default        -> null;
        };
    }
}