package com.adit.mockDemo.dto;

import com.adit.mockDemo.chaos.execution.ChaosType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Aggregated chaos analytics for a time window")
public class ChaosAnalyticsResponse {

    private Instant from;
    private Instant to;
    private String windowLabel;           // "Last 24h", "Last 7d", etc.

    // ── Summary ──────────────────────────────────────────────────────────────
    private Long totalEvents;
    private Long injectedCount;
    private Long skippedCount;
    private Double injectionRate;         // injected / total
    private Double avgInjectedLatencyMs;  // avg delay when chaos was latency type

    // ── Top targets ───────────────────────────────────────────────────────────
    private List<TargetStat> topTargets;

    // ── Chaos type breakdown ──────────────────────────────────────────────────
    private Map<ChaosType, Long> typeBreakdown;

    // ── Hourly time-series ────────────────────────────────────────────────────
    private List<TimeSeriesPoint> timeSeries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetStat {
        private String target;
        private Long injectionCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesPoint {
        private String hour;
        private Long total;
        private Long injected;
        private Double avgDelayMs;
    }
}