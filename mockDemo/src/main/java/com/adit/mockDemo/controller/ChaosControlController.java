package com.adit.mockDemo.controller;

import com.adit.mockDemo.chaos.execution.ChaosKillSwitch;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/chaos/control")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chaos Control", description = "Emergency controls for chaos injection")
@SecurityRequirement(name = "ApiKey")
public class ChaosControlController {

    private final ChaosKillSwitch killSwitch;
    private final TenantContext   tenantContext;

    @GetMapping("/status")
    @Operation(summary = "Get chaos status for the authenticated organization")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("GET /api/v1/chaos/control/status - Org: {}", org.getSlug());

        boolean active = killSwitch.isChaosEnabled(org.getId());
        return ResponseEntity.ok(Map.of(
                "enabled", active,
                "message", active
                        ? "Chaos injection is ACTIVE"
                        : "Chaos injection is DISABLED (kill switch activated)"
        ));
    }

    @PostMapping("/enable")
    @Operation(summary = "Enable chaos injection for this organization")
    public ResponseEntity<Map<String, Object>> enableChaos() {
        Organization org = tenantContext.getCurrentOrganization();
        log.warn("POST /api/v1/chaos/control/enable - Enabling chaos for org: {}", org.getSlug());

        killSwitch.enableChaos(org.getId());
        return ResponseEntity.ok(Map.of(
                "enabled", true,
                "message", "Chaos injection enabled"
        ));
    }

    @PostMapping("/disable")
    @Operation(
            summary = "Disable chaos injection for this organization (EMERGENCY STOP)",
            description = "⚠️ EMERGENCY: Instantly stops ALL chaos injection for this organization"
    )
    public ResponseEntity<Map<String, Object>> disableChaos() {
        Organization org = tenantContext.getCurrentOrganization();
        log.error("POST /api/v1/chaos/control/disable - KILL SWITCH for org: {}", org.getSlug());

        killSwitch.disableChaos(org.getId());
        return ResponseEntity.ok(Map.of(
                "enabled", false,
                "message", "🛑 EMERGENCY STOP: Chaos injection disabled"
        ));
    }

    @PostMapping("/toggle")
    @Operation(summary = "Toggle chaos injection state for this organization")
    public ResponseEntity<Map<String, Object>> toggleChaos() {
        Organization org = tenantContext.getCurrentOrganization();
        log.warn("POST /api/v1/chaos/control/toggle - Org: {}", org.getSlug());

        boolean newState = killSwitch.toggle(org.getId());
        return ResponseEntity.ok(Map.of(
                "enabled", newState,
                "message", newState ? "Chaos enabled" : "Chaos disabled"
        ));
    }
}