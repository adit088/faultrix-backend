package com.adit.mockDemo.chaos;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChaosRegistry {

    private final Map<String, ChaosRule> rules = new ConcurrentHashMap<>();

    public ChaosRegistry(){

        // default rule hai
        rules.put("default", new ChaosRule(0.0, 0, false));
    }

    public ChaosRule getRule(String target){
        return rules.getOrDefault(target, rules.get("default"));
    }

    public void upsertRule(String target, ChaosRule rule){
        rules.put(target, rule);
    }

    public void saveRule(String target, ChaosRule rule){
        rules.put(target, rule);
    }

}
