package com.adit.mockDemo.controller;

import com.adit.mockDemo.dto.ChaosScheduleRequest;
import com.adit.mockDemo.dto.ChaosScheduleResponse;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.security.TenantContext;
import com.adit.mockDemo.service.ChaosScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chaos/rules/{ruleId}/schedules")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chaos Schedules", description = "Time-window scheduling for chaos rules")
@SecurityRequirement(name = "ApiKey")
public class ChaosScheduleController {

    private final ChaosScheduleService scheduleService;
    private final TenantContext        tenantContext;

    @GetMapping
    @Operation(summary = "List schedules for a rule",
            description = "Returns all schedule windows for the specified chaos rule.")
    public ResponseEntity<List<ChaosScheduleResponse>> getSchedules(@PathVariable Long ruleId) {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("GET /api/v1/chaos/rules/{}/schedules - Org: {}", ruleId, org.getSlug());
        return ResponseEntity.ok(scheduleService.getSchedulesForRule(org, ruleId));
    }

    @PostMapping
    @Operation(summary = "Create a schedule window",
            description = "Add a time window to a chaos rule. Chaos only fires during active windows.")
    public ResponseEntity<ChaosScheduleResponse> createSchedule(
            @PathVariable Long ruleId,
            @Valid @RequestBody ChaosScheduleRequest request) {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("POST /api/v1/chaos/rules/{}/schedules - Org: {}", ruleId, org.getSlug());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduleService.createSchedule(org, ruleId, request));
    }

    @PutMapping("/{scheduleId}")
    @Operation(summary = "Update a schedule window")
    public ResponseEntity<ChaosScheduleResponse> updateSchedule(
            @PathVariable Long ruleId,
            @PathVariable Long scheduleId,
            @Valid @RequestBody ChaosScheduleRequest request) {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("PUT /api/v1/chaos/rules/{}/schedules/{} - Org: {}", ruleId, scheduleId, org.getSlug());
        return ResponseEntity.ok(scheduleService.updateSchedule(org, ruleId, scheduleId, request));
    }

    @DeleteMapping("/{scheduleId}")
    @Operation(summary = "Delete a schedule window")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable Long ruleId,
            @PathVariable Long scheduleId) {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("DELETE /api/v1/chaos/rules/{}/schedules/{} - Org: {}", ruleId, scheduleId, org.getSlug());
        scheduleService.deleteSchedule(org, ruleId, scheduleId);
        return ResponseEntity.noContent().build();
    }
}