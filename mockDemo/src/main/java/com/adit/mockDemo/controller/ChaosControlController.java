package com.adit.mockDemo.controller;

import com.adit.mockDemo.chaos.execution.ChaosKillSwitch;
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

    @GetMapping("/status")
    @Operation(summary = "Get global chaos status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        log.info("GET /api/v1/chaos/control/status");
        boolean active = killSwitch.isChaosEnabled();
        return ResponseEntity.ok(Map.of(
                "chaosEnabled", active,
                "message", active
                        ? "Chaos injection is ACTIVE"
                        : "Chaos injection is DISABLED (kill switch activated)"
        ));
    }

    @PostMapping("/enable")
    @Operation(summary = "Enable all chaos injection globally")
    public ResponseEntity<Map<String, Object>> enableChaos() {
        log.warn("POST /api/v1/chaos/control/enable - Enabling chaos globally");
        killSwitch.enableChaos();
        return ResponseEntity.ok(Map.of(
                "chaosEnabled", true,
                "message", "Chaos injection enabled globally"
        ));
    }

    @PostMapping("/disable")
    @Operation(
            summary = "Disable all chaos injection globally (EMERGENCY STOP)",
            description = "⚠️ EMERGENCY: Instantly stops ALL chaos injection across all organizations"
    )
    public ResponseEntity<Map<String, Object>> disableChaos() {
        log.error("POST /api/v1/chaos/control/disable - KILL SWITCH ACTIVATED");
        killSwitch.disableChaos();
        return ResponseEntity.ok(Map.of(
                "chaosEnabled", false,
                "message", "🛑 EMERGENCY STOP: All chaos injection disabled"
        ));
    }

    @PostMapping("/toggle")
    @Operation(summary = "Toggle chaos injection state")
    public ResponseEntity<Map<String, Object>> toggleChaos() {
        log.warn("POST /api/v1/chaos/control/toggle");
        boolean newState = killSwitch.toggle();
        return ResponseEntity.ok(Map.of(
                "chaosEnabled", newState,
                "message", newState ? "Chaos enabled" : "Chaos disabled"
        ));
    }
}