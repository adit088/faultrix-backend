package com.adit.mockDemo.controller;

import com.adit.mockDemo.metrics.ChaosMetrics;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChaosExperimentController {

    private final ChaosMetrics chaosMetrics;

    public ChaosExperimentController(ChaosMetrics chaosMetrics) {
        this.chaosMetrics = chaosMetrics;
    }

    @PostMapping("/chaos/reset")
    public void resetExperiment() {
        chaosMetrics.resetExperiment();
    }
}
