package com.adit.mockDemo.controller;

import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.metrics.ChaosMetrics;
import com.adit.mockDemo.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experiments/control")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Chaos Experiments", description = "Manage chaos experiments")
@SecurityRequirement(name = "ApiKey")
public class ChaosExperimentController {

    private final ChaosMetrics chaosMetrics;
    private final TenantContext tenantContext;

    @PostMapping("/reset")
    @Operation(summary = "Reset experiment", description = "Reset experiment metrics for the current organization")
    public ResponseEntity<String> resetExperiment() {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("POST /api/v1/experiments/control/reset - Org: {}", org.getSlug());

        chaosMetrics.resetExperiment();
        return ResponseEntity.ok("Experiment reset successfully");
    }
}