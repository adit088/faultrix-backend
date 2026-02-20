package com.adit.mockDemo.insights;

import com.adit.mockDemo.chaos.ChaosRule;

/**
 * Calculates confidence scores and priority rankings for insights
 */
public class InsightScorer {

    /**
     * Calculate confidence score (0.0-1.0) based on sample size and variance
     */
    public static double calculateConfidence(long sampleSize, double variance) {
        // Base confidence from sample size
        double sizeConfidence = Math.min(1.0, sampleSize / 100.0);

        // Reduce confidence if high variance (inconsistent results)
        double variancePenalty = Math.max(0.0, 1.0 - variance);

        return sizeConfidence * variancePenalty;
    }

    /**
     * Calculate priority score (1-100) for insight urgency
     */
    public static int calculatePriority(InsightLevel level,
                                        double confidence,
                                        int affectedRequests,
                                        double failureMultiplier) {
        int baseScore = switch (level) {
            case CRITICAL -> 80;
            case WARNING -> 50;
            case INFO -> 20;
            case SUCCESS -> 10;
        };

        // Boost by confidence (max +10)
        int confidenceBoost = (int) (confidence * 10);

        // Boost by impact (max +5)
        int impactBoost = Math.min(5, affectedRequests / 100);

        // Boost by severity multiplier (max +5)
        int severityBoost = (int) Math.min(5, failureMultiplier - 1.0);

        return Math.min(100, baseScore + confidenceBoost + impactBoost + severityBoost);
    }

    /**
     * Estimate cost impact (simplified - can be enhanced with business metrics)
     */
    public static String estimateCost(int affectedRequests,
                                      double failureRate,
                                      ChaosRule rule) {
        if (affectedRequests < 10) {
            return "Low: Minimal revenue impact";
        }

        // Simplified cost model (can be enhanced with actual business metrics)
        double hourlyRequests = affectedRequests * (3600.0 / (rule.getMaxDelayMs() != null ? rule.getMaxDelayMs() : 1000));
        double hourlyFailures = hourlyRequests * failureRate;

        if (hourlyFailures < 100) {
            return "Medium: ~$500/hour estimated impact";
        } else if (hourlyFailures < 1000) {
            return String.format("High: ~$%.1fK/hour estimated impact", hourlyFailures * 5 / 1000);
        } else {
            return String.format("Critical: ~$%.0fK/hour estimated impact", hourlyFailures * 5 / 1000);
        }
    }

    /**
     * Determine trend direction from time-series data
     */
    public static String analyzeTrend(long recentFailures, long historicalFailures) {
        if (historicalFailures == 0) {
            return "NEW";
        }

        double changeRate = (double) (recentFailures - historicalFailures) / historicalFailures;

        if (changeRate > 0.2) {
            return "WORSENING";
        } else if (changeRate < -0.2) {
            return "IMPROVING";
        } else {
            return "STABLE";
        }
    }

    /**
     * Calculate trend percentage change
     */
    public static double calculateTrendPercentage(long current, long previous) {
        if (previous == 0) {
            return 0.0;
        }
        return ((double) (current - previous) / previous) * 100;
    }
}