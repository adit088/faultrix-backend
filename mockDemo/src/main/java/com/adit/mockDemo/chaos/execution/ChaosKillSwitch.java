package com.adit.mockDemo.chaos.execution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-tenant kill switch for chaos injection.
 *
 * FIX SEC-7: replaced single global AtomicBoolean with a ConcurrentHashMap<orgId, AtomicBoolean>.
 * Previously one org's admin (or a compromised key) could disable chaos for ALL tenants globally.
 * Now each organization has an independent switch; one org cannot affect another.
 *
 * Controllers pass the org ID from TenantContext when calling enable/disable/toggle/isChaosEnabled.
 */
@Component
@Slf4j
public class ChaosKillSwitch {

    // Per-tenant state — absent key means "enabled" (default)
    private final ConcurrentHashMap<Long, AtomicBoolean> tenantSwitches = new ConcurrentHashMap<>();

    // ── Per-tenant API (used by controllers) ─────────────────────────────────

    public boolean isChaosEnabled(Long orgId) {
        return getSwitch(orgId).get();
    }

    public void enableChaos(Long orgId) {
        getSwitch(orgId).set(true);
        log.warn("🚀 CHAOS ENABLED for org={}", orgId);
    }

    public void disableChaos(Long orgId) {
        getSwitch(orgId).set(false);
        log.error("🛑 CHAOS DISABLED (kill switch) for org={}", orgId);
    }

    /**
     * Atomic toggle — race-free via compareAndSet loop.
     * @return new state after toggle
     */
    public boolean toggle(Long orgId) {
        AtomicBoolean sw = getSwitch(orgId);
        boolean current, next;
        do {
            current = sw.get();
            next    = !current;
        } while (!sw.compareAndSet(current, next));

        log.warn("Chaos toggled for org={}: {}", orgId, next ? "ENABLED" : "DISABLED");
        return next;
    }

    // ── No-arg overloads kept for backward-compat with existing callers ───────
    // These operate on a special "global" sentinel key (orgId = 0L).
    // New code should always pass the real orgId.

    /** @deprecated Pass orgId — use isChaosEnabled(Long) */
    @Deprecated
    public boolean isChaosEnabled() {
        return getSwitch(0L).get();
    }

    /** @deprecated Pass orgId — use enableChaos(Long) */
    @Deprecated
    public void enableChaos() {
        enableChaos(0L);
    }

    /** @deprecated Pass orgId — use disableChaos(Long) */
    @Deprecated
    public void disableChaos() {
        disableChaos(0L);
    }

    /** @deprecated Pass orgId — use toggle(Long) */
    @Deprecated
    public boolean toggle() {
        return toggle(0L);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private AtomicBoolean getSwitch(Long orgId) {
        return tenantSwitches.computeIfAbsent(orgId, k -> new AtomicBoolean(true));
    }
}