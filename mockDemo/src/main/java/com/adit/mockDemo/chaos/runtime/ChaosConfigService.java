package com.adit.mockDemo.chaos.runtime;

import lombok.Getter;
import org.springframework.stereotype.Service;

// stores & updates chaos values
@Getter
@Service
public class ChaosConfigService {

    private volatile double failureRate;
    private volatile long maxDelayMs;

    public ChaosConfigService(){

        // default value (can be overwritten at runtime)
        this.failureRate = 0.3;
        this.maxDelayMs = 2000;
    }

    public void update(double failureRate, long maxDelayMs){
        this.failureRate = failureRate;
        this.maxDelayMs = maxDelayMs;
    }
}
