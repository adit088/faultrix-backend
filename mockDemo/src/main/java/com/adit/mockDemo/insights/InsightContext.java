package com.adit.mockDemo.insights;

import com.adit.mockDemo.chaos.ChaosRule;

public class InsightContext {

        private final ChaosRule rule;
        private final long failures;
        private final long delays;
        private final int trafficUsers;

        public InsightContext(
                ChaosRule rule,
                long failures,
                long delays,
                int trafficUsers
        ) {
            this.rule = rule;
            this.failures = failures;
            this.delays = delays;
            this.trafficUsers = trafficUsers;
        }

        public ChaosRule getRule() { return rule; }
        public long getFailures() { return failures; }
        public long getDelays() { return delays; }
        public int getTrafficUsers() { return trafficUsers; }
    }
