package com.adit.mockDemo.chaos.execution;

import com.adit.mockDemo.chaos.ChaosRule;
import com.adit.mockDemo.entity.ChaosSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChaosDecisionEngineTest {

    @Mock
    private ChaosKillSwitch killSwitch;

    @Mock
    private ScheduleEvaluator scheduleEvaluator;

    @InjectMocks
    private ChaosDecisionEngine engine;

    private ChaosRule testRule;

    @BeforeEach
    void setUp() {
        testRule = ChaosRule.builder()
                .target("/api/v1/users")
                .enabled(true)
                .failureRate(0.5)
                .maxDelayMs(100L)
                .blastRadius(1.0)
                .seed(42L)
                .build();
    }

    @Test
    void decide_whenKillSwitchDisabled_thenNoChaos() {
        when(killSwitch.isChaosEnabled()).thenReturn(false);

        ChaosDecision decision = engine.decide(testRule, "req-123", Collections.emptyList());

        assertThat(decision.isShouldInjectChaos()).isFalse();
    }

    @Test
    void decide_whenRuleDisabled_thenNoChaos() {
        when(killSwitch.isChaosEnabled()).thenReturn(true);

        ChaosRule disabledRule = ChaosRule.builder()
                .target("/api/v1/users")
                .enabled(false)
                .failureRate(0.5)
                .maxDelayMs(100L)
                .blastRadius(1.0)
                .build();

        ChaosDecision decision = engine.decide(disabledRule, "req-123", Collections.emptyList());

        assertThat(decision.isShouldInjectChaos()).isFalse();
    }

    @Test
    void decide_whenScheduleInactive_thenNoChaos() {
        when(killSwitch.isChaosEnabled()).thenReturn(true);
        when(scheduleEvaluator.isActiveNow(anyList())).thenReturn(false);

        ChaosDecision decision = engine.decide(testRule, "req-123", Collections.emptyList());

        assertThat(decision.isShouldInjectChaos()).isFalse();
    }

    @Test
    void decide_whenFailureRate100_thenAlwaysInjectChaos() {
        when(killSwitch.isChaosEnabled()).thenReturn(true);
        when(scheduleEvaluator.isActiveNow(anyList())).thenReturn(true);

        ChaosRule alwaysFailRule = ChaosRule.builder()
                .target("/api/v1/users")
                .enabled(true)
                .failureRate(1.0)
                .maxDelayMs(100L)
                .blastRadius(1.0)
                .seed(42L)
                .build();

        ChaosDecision decision = engine.decide(alwaysFailRule, "req-123", Collections.emptyList());

        assertThat(decision.isShouldInjectChaos()).isTrue();
        assertThat(decision.getChaosType()).isNotNull();
    }

    @Test
    void decide_whenFailureRate0_thenNeverInjectChaos() {
        when(killSwitch.isChaosEnabled()).thenReturn(true);
        when(scheduleEvaluator.isActiveNow(anyList())).thenReturn(true);

        ChaosRule neverFailRule = ChaosRule.builder()
                .target("/api/v1/users")
                .enabled(true)
                .failureRate(0.0)
                .maxDelayMs(100L)
                .blastRadius(1.0)
                .build();

        ChaosDecision decision = engine.decide(neverFailRule, "req-123", Collections.emptyList());

        assertThat(decision.isShouldInjectChaos()).isFalse();
    }

    @Test
    void decide_whenBlastRadius0_thenNoChaos() {
        when(killSwitch.isChaosEnabled()).thenReturn(true);
        when(scheduleEvaluator.isActiveNow(anyList())).thenReturn(true);

        ChaosRule noBlastRule = ChaosRule.builder()
                .target("/api/v1/users")
                .enabled(true)
                .failureRate(1.0)
                .maxDelayMs(100L)
                .blastRadius(0.0)
                .build();

        ChaosDecision decision = engine.decide(noBlastRule, "req-123", Collections.emptyList());

        assertThat(decision.isShouldInjectChaos()).isFalse();
    }

    @Test
    void decide_whenMaxDelayMs100_thenDelayWithinRange() {
        when(killSwitch.isChaosEnabled()).thenReturn(true);
        when(scheduleEvaluator.isActiveNow(anyList())).thenReturn(true);

        ChaosRule delayRule = ChaosRule.builder()
                .target("/api/v1/users")
                .enabled(true)
                .failureRate(1.0)
                .maxDelayMs(100L)
                .blastRadius(1.0)
                .seed(42L)
                .build();

        ChaosDecision decision = engine.decide(delayRule, "req-123", Collections.emptyList());

        if (decision.isShouldInjectChaos()) {
            assertThat(decision.getDelayMs()).isBetween(0, 100);
        }
    }

    @Test
    void decide_deterministicForSameRequestId() {
        when(killSwitch.isChaosEnabled()).thenReturn(true);
        when(scheduleEvaluator.isActiveNow(anyList())).thenReturn(true);

        ChaosDecision decision1 = engine.decide(testRule, "same-req-id", Collections.emptyList());
        ChaosDecision decision2 = engine.decide(testRule, "same-req-id", Collections.emptyList());

        assertThat(decision1.isShouldInjectChaos()).isEqualTo(decision2.isShouldInjectChaos());
        if (decision1.isShouldInjectChaos()) {
            assertThat(decision1.getDelayMs()).isEqualTo(decision2.getDelayMs());
        }
    }
}