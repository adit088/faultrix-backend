package com.adit.mockDemo.chaos.runtime;


import com.adit.mockDemo.chaos.ChaosRegistry;
import com.adit.mockDemo.chaos.ChaosRule;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// REST API
@RestController
public class ChaosConfigController {

    private final ChaosRegistry chaosRegistry;

    public ChaosConfigController(ChaosRegistry chaosRegistry){
        this.chaosRegistry = chaosRegistry;
    }

    @PostMapping("/chaos/rules")
    public void setRule(@RequestBody ChaosRuleRequest request){

        ChaosRule rule = new ChaosRule(
                request.getFailureRate(),
                request.getMaxDelayMs(),
                request.isEnabled()
        );

        chaosRegistry.saveRule(request.getTarget(), rule);

        System.out.println(
                "CHAOS RULE SAVED → target=" + request.getTarget()
                        + ", enabled=" + rule.isEnabled()
        );
    }
}
