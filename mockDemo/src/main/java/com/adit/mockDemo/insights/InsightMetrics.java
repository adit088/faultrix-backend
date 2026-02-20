package com.adit.mockDemo.insights;

import com.adit.mockDemo.chaos.execution.ChaosType;
import com.adit.mockDemo.entity.ChaosEvent;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pre-calculated metrics from chaos events to avoid repeated computations
 */
@Getter
@Builder
public class InsightMetrics {

    // ── Event Counts ──────────────────────────────────────────────────────────
    private final long totalEvents;
    private final long injectedEvents;
    private final long failureEvents;
    private final long latencyOnlyEvents;
    private final long successfulEvents;

    // ── Request Tracking ──────────────────────────────────────────────────────
    private final Set<String> uniqueRequestIds;
    private final Set<String> affectedRequestIds;
    private final double actualBlastRadius;

    // ── Failure Analysis ──────────────────────────────────────────────────────
    private final double observedFailureRate;
    private final Map<ChaosType, Long> typeDistribution;
    private final long error5xxCount;
    private final long error4xxCount;
    private final long timeoutCount;

    // ── Timing Analysis ───────────────────────────────────────────────────────
    private final double avgDelayMs;
    private final double avgRecoveryMs;
    private final Map<Integer, Long> hourlyDistribution;

    // ── Trend Data ────────────────────────────────────────────────────────────
    private final List<ChaosEvent> sortedByTime;

    /**
     * Build metrics from event list
     */
    public static InsightMetrics calculate(List<ChaosEvent> events) {
        if (events.isEmpty()) {
            return InsightMetrics.builder()
                    .totalEvents(0)
                    .injectedEvents(0)
                    .failureEvents(0)
                    .latencyOnlyEvents(0)
                    .successfulEvents(0)
                    .uniqueRequestIds(Set.of())
                    .affectedRequestIds(Set.of())
                    .actualBlastRadius(0.0)
                    .observedFailureRate(0.0)
                    .typeDistribution(Map.of())
                    .error5xxCount(0)
                    .error4xxCount(0)
                    .timeoutCount(0)
                    .avgDelayMs(0.0)
                    .avgRecoveryMs(0.0)
                    .hourlyDistribution(Map.of())
                    .sortedByTime(List.of())
                    .build();
        }

        long totalEvents = events.size();
        long injectedEvents = events.stream().filter(ChaosEvent::getInjected).count();

        long failureEvents = events.stream()
                .filter(e -> e.getInjected() &&
                        e.getChaosType() != ChaosType.LATENCY &&
                        e.getChaosType() != ChaosType.NONE)
                .count();

        long latencyOnlyEvents = events.stream()
                .filter(e -> e.getInjected() && e.getChaosType() == ChaosType.LATENCY)
                .count();

        long successfulEvents = events.stream()
                .filter(e -> !e.getInjected() || e.getChaosType() == ChaosType.NONE)
                .count();

        Set<String> uniqueRequestIds = events.stream()
                .map(ChaosEvent::getRequestId)
                .collect(Collectors.toSet());

        Set<String> affectedRequestIds = events.stream()
                .filter(ChaosEvent::getInjected)
                .map(ChaosEvent::getRequestId)
                .collect(Collectors.toSet());

        double actualBlastRadius = uniqueRequestIds.isEmpty()
                ? 0.0
                : (double) affectedRequestIds.size() / uniqueRequestIds.size();

        double observedFailureRate = totalEvents == 0
                ? 0.0
                : (double) failureEvents / totalEvents;

        Map<ChaosType, Long> typeDistribution = events.stream()
                .filter(ChaosEvent::getInjected)
                .collect(Collectors.groupingBy(ChaosEvent::getChaosType, Collectors.counting()));

        long error5xxCount = typeDistribution.getOrDefault(ChaosType.ERROR_5XX, 0L);
        long error4xxCount = typeDistribution.getOrDefault(ChaosType.ERROR_4XX, 0L);
        long timeoutCount = typeDistribution.getOrDefault(ChaosType.TIMEOUT, 0L);

        double avgDelayMs = events.stream()
                .filter(e -> e.getDelayMs() > 0)
                .mapToInt(ChaosEvent::getDelayMs)
                .average()
                .orElse(0.0);

        Map<Integer, Long> hourlyDistribution = events.stream()
                .filter(ChaosEvent::getInjected)
                .collect(Collectors.groupingBy(
                        e -> e.getOccurredAt().atZone(java.time.ZoneOffset.UTC).getHour(),
                        Collectors.counting()
                ));

        List<ChaosEvent> sortedByTime = events.stream()
                .sorted((a, b) -> a.getOccurredAt().compareTo(b.getOccurredAt()))
                .toList();

        return InsightMetrics.builder()
                .totalEvents(totalEvents)
                .injectedEvents(injectedEvents)
                .failureEvents(failureEvents)
                .latencyOnlyEvents(latencyOnlyEvents)
                .successfulEvents(successfulEvents)
                .uniqueRequestIds(uniqueRequestIds)
                .affectedRequestIds(affectedRequestIds)
                .actualBlastRadius(actualBlastRadius)
                .observedFailureRate(observedFailureRate)
                .typeDistribution(typeDistribution)
                .error5xxCount(error5xxCount)
                .error4xxCount(error4xxCount)
                .timeoutCount(timeoutCount)
                .avgDelayMs(avgDelayMs)
                .avgRecoveryMs(0.0) // Calculated separately
                .hourlyDistribution(hourlyDistribution)
                .sortedByTime(sortedByTime)
                .build();
    }
}