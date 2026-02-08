package com.adit.mockDemo.chaos.runtime;

import lombok.Getter;

@Getter
public class ChaosRuleRequest {

    private String target;
    private double failureRate;
    private long maxDelayMs;
    private boolean enabled;

}
