package com.adit.mockDemo.chaos.runtime;

import com.adit.mockDemo.chaos.ChaosRule;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.repository.OrganizationRepository;
import com.adit.mockDemo.service.ChaosRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChaosConfigService {

    private final ChaosRuleService chaosRuleService;
    private final OrganizationRepository organizationRepository;

    public ChaosRule getRuleForTarget(Long organizationId, String target) {
        log.debug("Fetching chaos rule for org ID: {}, target: {}", organizationId, target);

        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + organizationId));

        return chaosRuleService.getRuleForChaosEngine(org, target);
    }

    public ChaosRule getRuleForTargetByApiKey(String apiKey, String target) {
        log.debug("Fetching chaos rule by API key for target: {}", target);

        Organization org = organizationRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new IllegalArgumentException("Invalid API key"));

        return chaosRuleService.getRuleForChaosEngine(org, target);
    }

    @Deprecated(since = "Phase 2", forRemoval = true)
    public ChaosRule getRuleForTarget(String target) {
        log.warn("⚠️ DEPRECATED: getRuleForTarget(target) - use getRuleForTarget(orgId, target)");

        Organization defaultOrg = organizationRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default organization not found"));

        return chaosRuleService.getRuleForChaosEngine(defaultOrg, target);
    }
}