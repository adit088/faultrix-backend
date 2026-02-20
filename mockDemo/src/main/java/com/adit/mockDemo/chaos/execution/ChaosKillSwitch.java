package com.adit.mockDemo.chaos.execution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Global kill switch for all chaos injection.
 * Allows instant emergency shutdown of chaos if things go wrong.
 */
@Component
@Slf4j
public class ChaosKillSwitch {

    private final AtomicBoolean enabled = new AtomicBoolean(true);

    public boolean isChaosEnabled() {
        return enabled.get();
    }

    public void enableChaos() {
        enabled.set(true);
        log.warn("🚀 CHAOS GLOBALLY ENABLED");
    }

    public void disableChaos() {
        enabled.set(false);
        log.error("🛑 CHAOS GLOBALLY DISABLED (KILL SWITCH ACTIVATED)");
    }

    /**
     * Atomic toggle using compareAndSet loop (still race-free).
     */
    public boolean toggle() {
        boolean current, newValue;
        do {
            current = enabled.get();
            newValue = !current;
        } while (!enabled.compareAndSet(current, newValue));

        log.warn("Chaos toggled: {}", newValue ? "ENABLED" : "DISABLED");
        return newValue;
    }
}