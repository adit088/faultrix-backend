package com.adit.mockDemo.chaos;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChaosRule {

    private double failureRate;
    private long maxDelayMs;
    private boolean enabled;


//    public boolean isEnabled(){
//        return enabled;
//    }

    public void update(double failureRate, long maxDelayMs, boolean enabled) {
        this.failureRate = failureRate;
        this.maxDelayMs = maxDelayMs;
        this.enabled = enabled;
    }
}
