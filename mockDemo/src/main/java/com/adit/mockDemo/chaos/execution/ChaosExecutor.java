package com.adit.mockDemo.chaos.execution;

import com.adit.mockDemo.chaos.ChaosRule;
import com.adit.mockDemo.entity.ChaosRuleEntity;
import com.adit.mockDemo.entity.ChaosSchedule;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.exception.ChaosInjectedException;
import com.adit.mockDemo.repository.ChaosRuleRepository;
import com.adit.mockDemo.repository.ChaosScheduleRepository;
import com.adit.mockDemo.security.TenantContext;
import com.adit.mockDemo.service.ChaosRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Executes chaos injection based on decisions from ChaosDecisionEngine.
 * Now schedule-aware — checks windows before injecting.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChaosExecutor {

    private final ChaosDecisionEngine  decisionEngine;
    private final ChaosRuleService     chaosRuleService;
    private final ChaosRuleRepository  chaosRuleRepository;
    private final ChaosScheduleRepository scheduleRepository;
    private final TenantContext        tenantContext;
    private final ChaosEventLogger     eventLogger;

    public void executeChaos(String target, String requestId) {
        try {
            Organization org  = tenantContext.getCurrentOrganization();
            ChaosRule    rule = chaosRuleService.getRuleForChaosEngine(org, target);

            // Fetch active schedules for this rule (empty = always active)
            List<ChaosSchedule> schedules = resolveSchedules(org, rule.getTarget());

            ChaosDecision decision = decisionEngine.decide(rule, requestId, schedules);

            eventLogger.logDecision(org, target, decision, requestId);

            if (decision.isShouldInjectChaos()) {
                executeDecision(decision);
            }

        } catch (ChaosInjectedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in chaos execution for target: {}", target, e);
        }
    }

    private List<ChaosSchedule> resolveSchedules(Organization org, String target) {
        try {
            return chaosRuleRepository
                    .findByOrganizationAndTarget(org, target)
                    .map(scheduleRepository::findByChaosRuleAndEnabledTrue)
                    .orElse(Collections.emptyList());
        } catch (Exception e) {
            log.warn("Could not resolve schedules for target: {} — defaulting to always active", target);
            return Collections.emptyList();
        }
    }

    private void executeDecision(ChaosDecision decision) {
        if (decision.getDelayMs() > 0) {
            injectLatency(decision.getDelayMs());
        }

        if (decision.isError() || decision.isException()) {
            throw new ChaosInjectedException(
                    decision.getErrorCode(),
                    decision.getErrorMessage(),
                    decision.getChaosType()
            );
        }
    }

    private void injectLatency(int delayMs) {
        try {
            log.debug("Injecting {}ms latency", delayMs);
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Latency injection interrupted", e);
        }
    }
}