package com.adit.mockDemo.service;

import com.adit.mockDemo.chaos.execution.ChaosDecision;
import com.adit.mockDemo.chaos.execution.ChaosType;
import com.adit.mockDemo.dto.ChaosAnalyticsResponse;
import com.adit.mockDemo.dto.ChaosEventResponse;
import com.adit.mockDemo.dto.PageResponse;
import com.adit.mockDemo.entity.ChaosEvent;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.repository.ChaosEventRepository;
import com.adit.mockDemo.repository.ChaosRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChaosEventService {

    private final ChaosEventRepository eventRepository;
    private final ChaosRuleRepository  ruleRepository;

    // ── Persistence ──────────────────────────────────────────────────────────

    @Async("chaosAsyncExecutor")
    public void recordEvent(Organization org,
                            String target,
                            String requestId,
                            ChaosDecision decision,
                            boolean injected) {
        try {
            var rule = ruleRepository
                    .findByOrganizationAndTarget(org, target)
                    .orElse(null);

            ChaosEvent event = ChaosEvent.builder()
                    .organization(org)
                    .chaosRule(rule)
                    .target(target)
                    .requestId(requestId)
                    .chaosType(decision.getChaosType() != null
                            ? decision.getChaosType()
                            : ChaosType.NONE)
                    .injected(injected)
                    .httpStatus(injected ? decision.getErrorCode() : null)
                    .delayMs(decision.getDelayMs())
                    .failureRate(rule != null ? rule.getFailureRate() : 0.0)
                    .blastRadius(rule != null ? rule.getBlastRadius() : 0.0)
                    .build();

            eventRepository.save(event);
            log.debug("Recorded chaos event: org={} target={} type={} injected={}",
                    org.getSlug(), target, event.getChaosType(), injected);

        } catch (Exception e) {
            log.error("Failed to persist chaos event for org={} target={}",
                    org.getSlug(), target, e);
        }
    }

    // ── Event History ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<ChaosEventResponse> getEvents(Organization org,
                                                      String target,
                                                      Instant from,
                                                      Instant to,
                                                      Boolean injected,
                                                      int limit,
                                                      int page) {

        log.info("GET chaos events - Org: {}, target: {}, from: {}, to: {}",
                org.getSlug(), target, from, to);

        PageRequest pageable = PageRequest.of(page, limit);

        List<ChaosEvent> events = eventRepository
                .findEvents(org.getId(), target, from, to, injected, pageable);

        List<ChaosEventResponse> responses = events.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        // FIX BUG-1: populate nextCursor so frontend "load more" works
        // nextCursor = ID of the last item returned; frontend passes it as ?cursor=N on next call
        Long nextCursor = null;
        boolean hasMore = responses.size() == limit;
        if (hasMore && !responses.isEmpty()) {
            nextCursor = responses.get(responses.size() - 1).getId();
        }

        PageResponse.PaginationMetadata meta = PageResponse.PaginationMetadata.builder()
                .nextCursor(nextCursor)
                .count(responses.size())
                .limit(limit)
                .hasMore(hasMore)
                .build();

        return PageResponse.<ChaosEventResponse>builder()
                .data(responses)
                .pagination(meta)
                .build();
    }

    // ── Analytics ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ChaosAnalyticsResponse getAnalytics(Organization org, String window) {
        log.info("GET chaos analytics - Org: {}, window: {}", org.getSlug(), window);

        Instant to   = Instant.now();
        Instant from = resolveWindow(window, to);

        long total    = eventRepository.countByOrganizationAndOccurredAtBetween(org, from, to);
        long injected = eventRepository.countInjectedBetween(org, from, to);
        long skipped  = total - injected;

        Double avgLatency = eventRepository.findAvgInjectedLatency(org, from, to);

        List<ChaosAnalyticsResponse.TargetStat> topTargets = eventRepository
                .findTopTargets(org, from, to, PageRequest.of(0, 10))
                .stream()
                .map(row -> ChaosAnalyticsResponse.TargetStat.builder()
                        .target((String) row[0])
                        .injectionCount(toLong(row[1]))
                        .build())
                .collect(Collectors.toList());

        Map<ChaosType, Long> typeBreakdown = eventRepository
                .findTypeBreakdown(org, from, to)
                .stream()
                .collect(Collectors.toMap(
                        row -> (ChaosType) row[0],
                        row -> toLong(row[1])
                ));

        List<ChaosAnalyticsResponse.TimeSeriesPoint> timeSeries = eventRepository
                .findHourlyTimeSeries(org.getId(), from)
                .stream()
                .map(row -> ChaosAnalyticsResponse.TimeSeriesPoint.builder()
                        .hour(String.valueOf(((Number) row[0]).intValue()))
                        .total(toLong(row[1]))
                        .injected(toLong(row[2]))
                        .avgDelayMs(toDouble(row[3]))
                        .build())
                .collect(Collectors.toList());

        return ChaosAnalyticsResponse.builder()
                .from(from)
                .to(to)
                .windowLabel(windowLabel(window))
                .totalEvents(total)
                .injectedCount(injected)
                .skippedCount(skipped)
                .injectionRate(total > 0 ? (double) injected / total : 0.0)
                .avgInjectedLatencyMs(avgLatency != null ? avgLatency : 0.0)
                .topTargets(topTargets)
                .typeBreakdown(typeBreakdown)
                .timeSeries(timeSeries)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Instant resolveWindow(String window, Instant now) {
        return switch (window.toLowerCase()) {
            case "1h"  -> now.minus(1,  ChronoUnit.HOURS);
            case "24h" -> now.minus(24, ChronoUnit.HOURS);
            case "7d"  -> now.minus(7,  ChronoUnit.DAYS);
            case "30d" -> now.minus(30, ChronoUnit.DAYS);
            default    -> now.minus(24, ChronoUnit.HOURS);
        };
    }

    private String windowLabel(String window) {
        return switch (window.toLowerCase()) {
            case "1h"  -> "Last 1 hour";
            case "24h" -> "Last 24 hours";
            case "7d"  -> "Last 7 days";
            case "30d" -> "Last 30 days";
            default    -> "Last 24 hours";
        };
    }

    private ChaosEventResponse mapToResponse(ChaosEvent e) {
        return ChaosEventResponse.builder()
                .id(e.getId())
                .organizationId(e.getOrganization().getId())
                .chaosRuleId(e.getChaosRule() != null ? e.getChaosRule().getId() : null)
                .target(e.getTarget())
                .requestId(e.getRequestId())
                .chaosType(e.getChaosType())
                .injected(e.getInjected())
                .httpStatus(e.getHttpStatus())
                .delayMs(e.getDelayMs())
                .failureRate(e.getFailureRate())
                .blastRadius(e.getBlastRadius())
                .occurredAt(e.getOccurredAt())
                .build();
    }

    private Long toLong(Object val) {
        if (val == null) return 0L;
        return ((Number) val).longValue();
    }

    private Double toDouble(Object val) {
        if (val == null) return 0.0;
        return ((Number) val).doubleValue();
    }
}