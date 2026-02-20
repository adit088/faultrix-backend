package com.adit.mockDemo.chaos.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChaosKillSwitchTest {

    @Test
    void isChaosEnabled_defaultTrue() {
        ChaosKillSwitch killSwitch = new ChaosKillSwitch();

        assertThat(killSwitch.isChaosEnabled()).isTrue();
    }

    @Test
    void disableChaos_setsEnabledFalse() {
        ChaosKillSwitch killSwitch = new ChaosKillSwitch();

        killSwitch.disableChaos();

        assertThat(killSwitch.isChaosEnabled()).isFalse();
    }

    @Test
    void enableChaos_setsEnabledTrue() {
        ChaosKillSwitch killSwitch = new ChaosKillSwitch();
        killSwitch.disableChaos();

        killSwitch.enableChaos();

        assertThat(killSwitch.isChaosEnabled()).isTrue();
    }

    @Test
    void toggle_flipsState() {
        ChaosKillSwitch killSwitch = new ChaosKillSwitch();
        boolean initialState = killSwitch.isChaosEnabled();

        boolean newState = killSwitch.toggle();

        assertThat(newState).isNotEqualTo(initialState);
        assertThat(killSwitch.isChaosEnabled()).isEqualTo(newState);
    }

    @Test
    void toggle_multipleTimes_flipsCorrectly() {
        ChaosKillSwitch killSwitch = new ChaosKillSwitch();

        killSwitch.toggle();
        assertThat(killSwitch.isChaosEnabled()).isFalse();

        killSwitch.toggle();
        assertThat(killSwitch.isChaosEnabled()).isTrue();

        killSwitch.toggle();
        assertThat(killSwitch.isChaosEnabled()).isFalse();
    }
}