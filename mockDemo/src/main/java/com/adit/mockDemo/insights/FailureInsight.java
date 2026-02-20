package com.adit.mockDemo.insights;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FailureInsight {

    // ── Core Fields (Required) ──────────────────────────────────────────────

    private InsightType type;
    private InsightLevel level;
    private String title;
    private String message;
    private String recommendation;

    // ── Metadata (Enrichment) ────────────────────────────────────────────────

    private Double confidenceScore;        // 0.0-1.0 (how confident we are)
    private Integer affectedRequests;      // Number of requests impacted
    private Double observedFailureRate;    // Actual failure rate observed
    private Double expectedFailureRate;    // Expected failure rate
    private Long avgRecoveryTimeMs;        // Average time to recover
    private Instant firstDetected;         // When this pattern first appeared
    private Instant lastDetected;          // Most recent occurrence

    // ── Impact Quantification ─────────────────────────────────────────────────

    private String estimatedImpact;        // "High: 45% of requests failing"
    private String estimatedCost;          // "~$2.4K/hour in lost revenue"
    private Integer priorityScore;         // 1-100 (composite urgency score)

    // ── Actionable Context ────────────────────────────────────────────────────

    private List<String> relatedTargets;   // Other endpoints with same issue
    private List<String> suggestedFixes;   // Multiple fix options ranked
    private String codeSnippet;            // Example fix code
    private String documentationUrl;       // Link to runbook/docs

    // ── Trend Data ────────────────────────────────────────────────────────────

    private String trend;                  // "WORSENING", "STABLE", "IMPROVING"
    private Integer occurrenceCount;       // How many times detected in dataset
    private Double trendPercentage;        // +15% worse than last week

    // ── Legacy Constructor (for backward compatibility) ──────────────────────

    public FailureInsight(InsightType type, InsightLevel level, String title,
                          String message, String recommendation) {
        this.type = type;
        this.level = level;
        this.title = title;
        this.message = message;
        this.recommendation = recommendation;
    }
}