package com.adit.mockDemo.insights;


import com.adit.mockDemo.chaos.ChaosRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class InsightEngine {

    public List<FailureInsight> analyze(ChaosRule rule, long failures, long delays, int trafficUsers){

        List<FailureInsight> insights = new ArrayList<>();

        if(failures > trafficUsers * rule.getFailureRate() * 1.5){
            insights.add(new FailureInsight(
                    InsightType.FAILURE_OVERRUN,
                    InsightLevel.CRITICAL,
                    "Failure Overrun",
                    "Observed error rate down and fails simultaneously.",
                    "Add circuit breakers to fail under pressure."
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
}
