package com.adit.mockDemo.insights;

import com.adit.mockDemo.chaos.ChaosRule;
import com.adit.mockDemo.chaos.execution.ChaosType;
import com.adit.mockDemo.entity.ChaosEvent;
import com.adit.mockDemo.entity.ChaosRuleEntity;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.repository.ChaosEventRepository;
import com.adit.mockDemo.repository.ChaosRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class InsightEngine {

    private final ChaosEventRepository eventRepository;
    private final ChaosRuleRepository ruleRepository;

    /**
     * Legacy analyze method - kept for backward compatibility
     */
    public List<FailureInsight> analyze(ChaosRule rule, long failures, long delays, int trafficUsers) {
        List<FailureInsight> insights = new ArrayList<>();

        if (failures > trafficUsers * rule.getFailureRate() * 1.5) {
            insights.add(new FailureInsight(
                    InsightType.FAILURE_OVERRUN,
                    InsightLevel.CRITICAL,
                    "Failure Overrun",
                    "Observed error rate exceeds expected rate significantly.",
                    "Add circuit breakers to prevent cascading failures."
            ));
        }

        if (trafficUsers < 20 && rule.getFailureRate() > 0.15) {
            insights.add(new FailureInsight(
                    InsightType.LOW_SAMPLE_BIAS,
                    InsightLevel.WARNING,
                    "Low Sample Bias",
                    "High failure rate with limited traffic reduces statistical confidence.",
                    "Increase traffic volume to validate system behavior under load."
            ));
        }

        if (trafficUsers == 0) {
            return insights;
        }

        double observedRate = (double) failures / trafficUsers;

        if (observedRate > rule.getFailureRate() * 2) {
            insights.add(new FailureInsight(
                    InsightType.CASCADING_FAILURE,
                    InsightLevel.CRITICAL,
                    "Cascading Failure Detected",
                    "Observed failures far exceed injected probability.",
                    "Inspect downstream dependencies and retry policies."
            ));
        }

        if (delays > trafficUsers * 0.6 && failures == 0) {
            insights.add(new FailureInsight(
                    InsightType.LATENCY_AMPLIFICATION,
                    InsightLevel.WARNING,
                    "Latency Amplification",
                    "Requests are slowing down without failing.",
                    "Reduce max delay or introduce timeout-based circuit breaking."
            ));
        }

        return insights;
    }

    /**
     * Full AI-powered insight generation with scoring and metadata
     */
    public List<FailureInsight> generateInsights(String target, Organization org) {
        log.info("Generating AI insights for target: {} (org: {})", target, org.getSlug());

        // Fetch last 1000 chaos events for this target
        List<ChaosEvent> events = eventRepository.findRecentByOrganizationAndTarget(
                org,
                target,
                PageRequest.of(0, 1000)
        );

        if (events.isEmpty()) {
            return List.of(FailureInsight.builder()
                    .type(InsightType.LOW_SAMPLE_BIAS)
                    .level(InsightLevel.INFO)
                    .title("No Data Yet")
                    .message("No chaos events recorded for target: " + target)
                    .recommendation("Run chaos experiments against this endpoint to generate insights.")
                    .confidenceScore(0.0)
                    .priorityScore(0)
                    .build());
        }

        // Get the active rule for this target
        Optional<ChaosRuleEntity> ruleEntityOpt = ruleRepository.findByOrganizationAndTarget(org, target);
        if (ruleEntityOpt.isEmpty()) {
            return List.of(FailureInsight.builder()
                    .type(InsightType.LOW_SAMPLE_BIAS)
                    .level(InsightLevel.INFO)
                    .title("No Active Rule")
                    .message("No chaos rule configured for target: " + target)
                    .recommendation("Create a chaos rule to enable systematic chaos testing.")
                    .confidenceScore(0.0)
                    .priorityScore(0)
                    .build());
        }

        ChaosRule rule = entityToRule(ruleEntityOpt.get());

        // Pre-calculate all metrics once (performance optimization)
        InsightMetrics metrics = InsightMetrics.calculate(events);

        // Run all AI detection algorithms with metrics
        List<FailureInsight> insights = new ArrayList<>();

        insights.addAll(detectCascadingFailures(events, rule, target, metrics));
        insights.addAll(detectLatencyAmplification(events, rule, target, metrics));
        insights.addAll(detectErrorTypeDistribution(events, target, metrics));
        insights.addAll(detectBlastRadiusMismatch(events, rule, target, metrics));
        insights.addAll(analyzeRecoveryTime(events, target, metrics));
        insights.addAll(detectTimeOfDayPatterns(events, target, metrics));

        // Sort by priority score (highest first)
        insights.sort((a, b) -> {
            if (b.getPriorityScore() != null && a.getPriorityScore() != null) {
                return b.getPriorityScore().compareTo(a.getPriorityScore());
            }
            int severityCompare = b.getLevel().compareTo(a.getLevel());
            if (severityCompare != 0) return severityCompare;
            return a.getTitle().compareTo(b.getTitle());
        });

        log.info("Generated {} AI insights for target: {}", insights.size(), target);
        return insights;
    }

    // ── AI Detection Algorithms (Enhanced with Scoring) ───────────────────────

    private List<FailureInsight> detectCascadingFailures(List<ChaosEvent> events, ChaosRule rule,
                                                         String target, InsightMetrics metrics) {
        double expectedFailures = metrics.getTotalEvents() * rule.getFailureRate();
        double failureMultiplier = metrics.getFailureEvents() / Math.max(expectedFailures, 1.0);

        if (failureMultiplier > 2.0 && metrics.getFailureEvents() > 10) {
            double confidence = InsightScorer.calculateConfidence(
                    metrics.getTotalEvents(),
                    0.1 // Low variance for cascading failures
            );

            int priority = InsightScorer.calculatePriority(
                    InsightLevel.CRITICAL,
                    confidence,
                    metrics.getAffectedRequestIds().size(),
                    failureMultiplier
            );

            String cost = InsightScorer.estimateCost(
                    metrics.getAffectedRequestIds().size(),
                    metrics.getObservedFailureRate(),
                    rule
            );

            return List.of(FailureInsight.builder()
                    .type(InsightType.CASCADING_FAILURE)
                    .level(InsightLevel.CRITICAL)
                    .title(String.format("Cascading Failure: %.0f%% blast radius breach", (failureMultiplier - 1) * 100))
                    .message(String.format(
                            "Expected %d failures (%.0f%% rate) but observed %d failures (%.0f%% rate). " +
                                    "This indicates downstream services are amplifying failures.",
                            (int) expectedFailures,
                            rule.getFailureRate() * 100,
                            metrics.getFailureEvents(),
                            metrics.getObservedFailureRate() * 100
                    ))
                    .recommendation(String.format(
                            "Add circuit breakers to downstream dependencies of %s. " +
                                    "Review retry policies and timeout configurations. " +
                                    "Consider implementing fail-fast patterns.",
                            target
                    ))
                    .confidenceScore(confidence)
                    .affectedRequests(metrics.getAffectedRequestIds().size())
                    .observedFailureRate(metrics.getObservedFailureRate())
                    .expectedFailureRate(rule.getFailureRate())
                    .estimatedImpact(String.format("High: %.0f%% of requests failing", metrics.getObservedFailureRate() * 100))
                    .estimatedCost(cost)
                    .priorityScore(priority)
                    .suggestedFixes(Arrays.asList(
                            "Implement circuit breaker with 50% failure threshold",
                            "Add exponential backoff to retry logic (2^n seconds)",
                            "Enable request hedging for critical paths"
                    ))
                    .trend("WORSENING")
                    .occurrenceCount((int) metrics.getFailureEvents())
                    .build());
        }

        return List.of();
    }

    private List<FailureInsight> detectLatencyAmplification(List<ChaosEvent> events, ChaosRule rule,
                                                            String target, InsightMetrics metrics) {
        if (metrics.getLatencyOnlyEvents() == 0) {
            return List.of();
        }

        long subsequentFailures = 0;
        for (int i = 0; i < events.size() - 5; i++) {
            ChaosEvent current = events.get(i);
            if (current.getInjected() && current.getChaosType() == ChaosType.LATENCY) {
                for (int j = i + 1; j < Math.min(i + 6, events.size()); j++) {
                    ChaosEvent next = events.get(j);
                    if (next.getInjected() &&
                            next.getChaosType() != ChaosType.LATENCY &&
                            next.getChaosType() != ChaosType.NONE) {
                        subsequentFailures++;
                        break;
                    }
                }
            }
        }

        double latencyToFailureRate = (double) subsequentFailures / metrics.getLatencyOnlyEvents();

        if (latencyToFailureRate > 0.5) {
            double confidence = InsightScorer.calculateConfidence(metrics.getLatencyOnlyEvents(), 0.2);
            int priority = InsightScorer.calculatePriority(
                    InsightLevel.WARNING,
                    confidence,
                    (int) subsequentFailures,
                    1.5
            );

            return List.of(FailureInsight.builder()
                    .type(InsightType.LATENCY_AMPLIFICATION)
                    .level(InsightLevel.WARNING)
                    .title("Latency Causes Failures")
                    .message(String.format(
                            "%.0f%% of latency injections led to subsequent failures within 5 requests. " +
                                    "This indicates missing timeouts or aggressive retry policies.",
                            latencyToFailureRate * 100
                    ))
                    .recommendation(String.format(
                            "Add timeouts to HTTP clients calling %s. " +
                                    "Configure exponential backoff on retries. " +
                                    "Consider implementing request hedging for critical paths.",
                            target
                    ))
                    .confidenceScore(confidence)
                    .affectedRequests((int) subsequentFailures)
                    .avgRecoveryTimeMs((long) metrics.getAvgDelayMs())
                    .estimatedImpact(String.format("Medium: %.0f%% latency-triggered failures", latencyToFailureRate * 100))
                    .priorityScore(priority)
                    .suggestedFixes(Arrays.asList(
                            "Set HTTP client timeout to 3000ms",
                            "Implement exponential backoff: delay = min(maxDelay, baseDelay * 2^attempt)",
                            "Add request hedging (send duplicate after 1s)"
                    ))
                    .build());
        }

        return List.of();
    }

    private List<FailureInsight> detectErrorTypeDistribution(List<ChaosEvent> events,
                                                             String target, InsightMetrics metrics) {
        if (metrics.getError5xxCount() > metrics.getError4xxCount() * 2 && metrics.getError5xxCount() > 10) {
            double confidence = InsightScorer.calculateConfidence(
                    metrics.getError5xxCount() + metrics.getError4xxCount(),
                    0.15
            );

            int priority = InsightScorer.calculatePriority(
                    InsightLevel.WARNING,
                    confidence,
                    (int) metrics.getError5xxCount(),
                    2.0
            );

            return List.of(FailureInsight.builder()
                    .type(InsightType.CIRCUIT_BREAKER_MISSING)
                    .level(InsightLevel.WARNING)
                    .title("High 5xx Error Rate")
                    .message(String.format(
                            "Injected %d 5xx errors vs %d 4xx errors. " +
                                    "Services should fail fast with 4xx when dependencies are unavailable.",
                            metrics.getError5xxCount(),
                            metrics.getError4xxCount()
                    ))
                    .recommendation(String.format(
                            "Implement circuit breakers that return 503 Service Unavailable when %s is degraded. " +
                                    "Avoid propagating 500 errors from dependencies.",
                            target
                    ))
                    .confidenceScore(confidence)
                    .affectedRequests((int) metrics.getError5xxCount())
                    .priorityScore(priority)
                    .suggestedFixes(Arrays.asList(
                            "Add circuit breaker library (e.g., Resilience4j, Hystrix)",
                            "Configure: failureRateThreshold=50%, waitDurationInOpenState=60s",
                            "Return 503 with Retry-After header when circuit open"
                    ))
                    .build());
        }

        return List.of();
    }

    private List<FailureInsight> detectBlastRadiusMismatch(List<ChaosEvent> events, ChaosRule rule,
                                                           String target, InsightMetrics metrics) {
        if (metrics.getUniqueRequestIds().isEmpty() || metrics.getUniqueRequestIds().size() < 50) {
            return List.of();
        }

        double configuredBlastRadius = rule.getBlastRadius();
        double deviation = Math.abs(metrics.getActualBlastRadius() - configuredBlastRadius);

        if (deviation > 0.15) {
            double confidence = InsightScorer.calculateConfidence(
                    metrics.getUniqueRequestIds().size(),
                    deviation
            );

            int priority = InsightScorer.calculatePriority(
                    InsightLevel.WARNING,
                    confidence,
                    metrics.getAffectedRequestIds().size(),
                    1.0
            );

            return List.of(FailureInsight.builder()
                    .type(InsightType.BLAST_RADIUS_MISMATCH)
                    .level(InsightLevel.WARNING)
                    .title("Blast Radius Mismatch")
                    .message(String.format(
                            "Configured blast radius: %.0f%%, Observed: %.0f%%. " +
                                    "Chaos may be affecting more/fewer requests than intended.",
                            configuredBlastRadius * 100,
                            metrics.getActualBlastRadius() * 100
                    ))
                    .recommendation(String.format(
                            "Review blast radius configuration for %s. " +
                                    "Verify request ID hashing is deterministic. " +
                                    "Check if caching is interfering with chaos injection.",
                            target
                    ))
                    .confidenceScore(confidence)
                    .affectedRequests(metrics.getAffectedRequestIds().size())
                    .priorityScore(priority)
                    .build());
        }

        return List.of();
    }

    private List<FailureInsight> analyzeRecoveryTime(List<ChaosEvent> events,
                                                     String target, InsightMetrics metrics) {
        List<ChaosEvent> sortedEvents = metrics.getSortedByTime();
        List<Duration> recoveryTimes = new ArrayList<>();

        for (int i = 0; i < sortedEvents.size() - 1; i++) {
            ChaosEvent current = sortedEvents.get(i);
            if (current.getInjected() &&
                    current.getChaosType() != ChaosType.LATENCY &&
                    current.getChaosType() != ChaosType.NONE) {
                for (int j = i + 1; j < sortedEvents.size(); j++) {
                    ChaosEvent next = sortedEvents.get(j);
                    if (!next.getInjected() || next.getChaosType() == ChaosType.NONE) {
                        Duration recovery = Duration.between(current.getOccurredAt(), next.getOccurredAt());
                        recoveryTimes.add(recovery);
                        break;
                    }
                }
            }
        }

        if (recoveryTimes.isEmpty()) {
            return List.of();
        }

        double avgRecoveryMs = recoveryTimes.stream()
                .mapToLong(Duration::toMillis)
                .average()
                .orElse(0);

        if (avgRecoveryMs > 5000) {
            double confidence = InsightScorer.calculateConfidence(recoveryTimes.size(), 0.2);
            int priority = InsightScorer.calculatePriority(
                    InsightLevel.WARNING,
                    confidence,
                    recoveryTimes.size(),
                    1.3
            );

            return List.of(FailureInsight.builder()
                    .type(InsightType.SLOW_RECOVERY)
                    .level(InsightLevel.WARNING)
                    .title("Slow Recovery Time")
                    .message(String.format(
                            "Average recovery time after chaos injection: %.0fms. " +
                                    "Services may be retrying aggressively or caching failures.",
                            avgRecoveryMs
                    ))
                    .recommendation(String.format(
                            "Implement exponential backoff with jitter for %s. " +
                                    "Add cache invalidation on errors. " +
                                    "Consider fail-fast patterns for faster recovery.",
                            target
                    ))
                    .confidenceScore(confidence)
                    .avgRecoveryTimeMs((long) avgRecoveryMs)
                    .priorityScore(priority)
                    .suggestedFixes(Arrays.asList(
                            "Add exponential backoff: baseDelay=100ms, multiplier=2, maxDelay=30s",
                            "Invalidate cache on 5xx errors",
                            "Implement circuit breaker to fail fast during outages"
                    ))
                    .build());
        }

        return List.of();
    }

    private List<FailureInsight> detectTimeOfDayPatterns(List<ChaosEvent> events,
                                                         String target, InsightMetrics metrics) {
        if (metrics.getTotalEvents() < 100) {
            return List.of();
        }

        if (metrics.getHourlyDistribution().isEmpty()) {
            return List.of();
        }

        OptionalDouble avgFailuresPerHour = metrics.getHourlyDistribution().values().stream()
                .mapToLong(Long::longValue)
                .average();

        if (avgFailuresPerHour.isEmpty()) {
            return List.of();
        }

        long maxFailures = metrics.getHourlyDistribution().values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);

        if (maxFailures > avgFailuresPerHour.getAsDouble() * 2) {
            Integer peakHour = metrics.getHourlyDistribution().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(0);

            double confidence = InsightScorer.calculateConfidence(metrics.getTotalEvents(), 0.3);
            int priority = InsightScorer.calculatePriority(
                    InsightLevel.INFO,
                    confidence,
                    (int) maxFailures,
                    1.0
            );

            return List.of(FailureInsight.builder()
                    .type(InsightType.PEAK_HOUR_FRAGILITY)
                    .level(InsightLevel.INFO)
                    .title("Traffic Pattern Detected")
                    .message(String.format(
                            "Peak chaos impact at %02d:00 UTC (%.0f%% above average). " +
                                    "System behavior varies significantly by traffic load.",
                            peakHour,
                            ((maxFailures / avgFailuresPerHour.getAsDouble()) - 1) * 100
                    ))
                    .recommendation(String.format(
                            "Consider running chaos tests during peak hours for %s. " +
                                    "Review auto-scaling policies and resource limits. " +
                                    "Validate system resilience under production load patterns.",
                            target
                    ))
                    .confidenceScore(confidence)
                    .priorityScore(priority)
                    .build());
        }

        return List.of();
    }

    // ── Helper Methods ─────────────────────────────────────────────────────────

    private ChaosRule entityToRule(ChaosRuleEntity entity) {
        return ChaosRule.builder()
                .target(entity.getTarget())
                .enabled(entity.getEnabled())
                .failureRate(entity.getFailureRate())
                .maxDelayMs(entity.getMaxDelayMs())
                .blastRadius(entity.getBlastRadius())
                .seed(entity.getSeed())
                .build();
    }
}