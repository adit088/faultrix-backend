package com.adit.mockDemo.chaos;


import com.adit.mockDemo.config.NtropiChaosProperties;
import com.adit.mockDemo.metrics.ChaosMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import com.adit.mockDemo.insights.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class ChaosEngine {

    private final Random random;
    private final ChaosRegistry ruleRegistry;
    private final ChaosMetrics chaosMetrics;
    private final InsightEngine insightEngine;

    public ChaosEngine(
            ChaosRegistry ruleRegistry,
            NtropiChaosProperties chaosProperties,
            ChaosMetrics chaosMetrics,
            InsightEngine insightEngine
    ) {
        this.ruleRegistry = ruleRegistry;
        this.random = new Random(chaosProperties.getSeed());
        this.chaosMetrics = chaosMetrics;
        this.insightEngine = insightEngine;
    }

    public ChaosDecision evaluate(String target) {

        ChaosRule rule = ruleRegistry.getRule(target);

        if (!rule.isEnabled()) {
            return new ChaosDecision(false, false, 0);
        }

        boolean fail = random.nextDouble() < rule.getFailureRate();

        if (fail) {
            chaosMetrics.recordFailure();
            return new ChaosDecision(true, false, 0);
        }

        if (rule.getMaxDelayMs() > 0) {
            long delayMs = random.nextInt((int) rule.getMaxDelayMs());
            chaosMetrics.recordDelay();
            return new ChaosDecision(false, true, delayMs);
        }

        return new ChaosDecision(false, false, 0);
    }

    public List<FailureInsight> generateInsights(String target) {

        ChaosRule rule = ruleRegistry.getRule(target);

        int traffic = chaosMetrics.getCurrentTraffic();

        // 🚫 No traffic = no experiment = no insights
        if (traffic == 0) {
            return List.of();
        }

        List<FailureInsight> insights = insightEngine.analyze(
                rule,
                chaosMetrics.getExperimentFailures(),
                chaosMetrics.getExperimentDelays(),
                traffic
        );

        // 🔥 Consume experiment so UI doesn't repeat verdicts
        chaosMetrics.resetExperiment();

        return insights;
    }
}
