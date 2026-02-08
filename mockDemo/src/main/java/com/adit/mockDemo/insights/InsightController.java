package com.adit.mockDemo.insights;

import com.adit.mockDemo.chaos.ChaosEngine;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chaos/insights")
public class InsightController {

    private final ChaosEngine chaosEngine;

    public InsightController(ChaosEngine chaosEngine) {
        this.chaosEngine = chaosEngine;
    }

    @GetMapping("/{target}")
    public List<FailureInsight> getInsights(@PathVariable String target) {
        return chaosEngine.generateInsights(target);
    }
}
