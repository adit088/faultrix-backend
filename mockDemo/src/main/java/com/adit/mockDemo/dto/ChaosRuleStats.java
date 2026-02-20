package com.adit.mockDemo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChaosRuleStats {
    private Long totalRules;           // Total rules for this org
    private Long enabledRules;         // How many are enabled
    private Long disabledRules;        // How many are disabled
    private Integer maxRulesAllowed;   // Plan limit (free=10, pro=100, etc)
    private Integer remainingRules;    // How many more can be created
    private String currentPlan;        // "free", "pro", "enterprise"
}