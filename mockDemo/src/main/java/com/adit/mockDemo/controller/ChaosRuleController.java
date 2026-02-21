package com.adit.mockDemo.controller;

import com.adit.mockDemo.dto.*;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.security.TenantContext;
import com.adit.mockDemo.service.ChaosRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chaos/rules")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chaos Rules", description = "Manage chaos engineering rules")
@SecurityRequirement(name = "ApiKey")
public class ChaosRuleController {

    private final ChaosRuleService chaosRuleService;
    private final TenantContext tenantContext;

    @GetMapping("/paginated")
    @Operation(
            summary = "Get chaos rules (paginated)",
            description = "✅ RECOMMENDED: Retrieve chaos rules with cursor-based pagination, filtering, and search."
    )
    public ResponseEntity<PageResponse<ChaosRuleResponse>> getRulesPaginated(
            @Parameter(description = "Cursor for next page") @RequestParam(required = false) Long cursor,
            @Parameter(description = "Items per page (1-100)") @RequestParam(required = false, defaultValue = "20") Integer limit,
            @Parameter(description = "Sort field") @RequestParam(required = false, defaultValue = "id") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(required = false, defaultValue = "ASC") String sortDirection,
            @Parameter(description = "Filter by enabled") @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "Filter by tags") @RequestParam(required = false) String tags,
            @Parameter(description = "Search query") @RequestParam(required = false) String search) {

        Organization org = tenantContext.getCurrentOrganization();
        log.info("GET /api/v1/chaos/rules/paginated - Org: {}", org.getSlug());

        PageRequest pageRequest = PageRequest.builder()
                .cursor(cursor)
                .limit(limit)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .enabled(enabled)
                .tags(tags)
                .search(search)
                .build();

        PageResponse<ChaosRuleResponse> response = chaosRuleService.getRulesPaginated(org, pageRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get chaos rules statistics")
    public ResponseEntity<ChaosRuleStats> getStats() {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("GET /api/v1/chaos/rules/stats - Org: {}", org.getSlug());

        ChaosRuleStats stats = chaosRuleService.getStats(org);
        return ResponseEntity.ok(stats);
    }

    @GetMapping
    @Operation(summary = "Get all chaos rules", description = "⚠️ Use /paginated for production")
    public ResponseEntity<List<ChaosRuleResponse>> getAllRules() {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("GET /api/v1/chaos/rules - Organization: {}", org.getSlug());

        List<ChaosRuleResponse> rules = chaosRuleService.getAllRules(org);
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get chaos rule by ID")
    public ResponseEntity<ChaosRuleResponse> getRuleById(@PathVariable Long id) {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("GET /api/v1/chaos/rules/{} - Organization: {}", id, org.getSlug());

        ChaosRuleResponse rule = chaosRuleService.getRuleById(org, id);
        return ResponseEntity.ok(rule);
    }

    @GetMapping("/target/{target}")
    @Operation(summary = "Get chaos rule by target")
    public ResponseEntity<ChaosRuleResponse> getRuleByTarget(@PathVariable String target) {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("GET /api/v1/chaos/rules/target/{} - Organization: {}", target, org.getSlug());

        ChaosRuleResponse rule = chaosRuleService.getRuleByTarget(org, target);
        return ResponseEntity.ok(rule);
    }

    @GetMapping("/enabled")
    @Operation(summary = "Get all enabled chaos rules", description = "⚠️ Use /paginated for production")
    public ResponseEntity<List<ChaosRuleResponse>> getEnabledRules() {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("GET /api/v1/chaos/rules/enabled - Organization: {}", org.getSlug());

        List<ChaosRuleResponse> rules = chaosRuleService.getEnabledRules(org);
        return ResponseEntity.ok(rules);
    }

    @PostMapping
    @Operation(summary = "Create a new chaos rule")
    public ResponseEntity<ChaosRuleResponse> createRule(@Valid @RequestBody ChaosRuleRequest request) {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("POST /api/v1/chaos/rules - Target: {}, Organization: {}", request.getTarget(), org.getSlug());

        ChaosRuleResponse created = chaosRuleService.createRule(org, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing chaos rule")
    public ResponseEntity<ChaosRuleResponse> updateRule(@PathVariable Long id, @Valid @RequestBody ChaosRuleRequest request) {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("PUT /api/v1/chaos/rules/{} - Organization: {}", id, org.getSlug());

        ChaosRuleResponse updated = chaosRuleService.updateRule(org, id, request);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a chaos rule (e.g. toggle enabled)")
    public ResponseEntity<ChaosRuleResponse> patchRule(@PathVariable Long id,
                                                       @RequestBody java.util.Map<String, Object> patch) {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("PATCH /api/v1/chaos/rules/{} - Organization: {}", id, org.getSlug());

        // Load existing rule, apply only the patched fields, then save via updateRule
        ChaosRuleResponse existing = chaosRuleService.getRuleById(org, id);

        ChaosRuleRequest request = com.adit.mockDemo.dto.ChaosRuleRequest.builder()
                .target(existing.getTarget())
                .targetPattern(existing.getTargetPattern())
                .targetingMode(existing.getTargetingMode())
                .failureRate(existing.getFailureRate())
                .maxDelayMs(existing.getMaxDelayMs())
                .enabled(patch.containsKey("enabled")
                        ? (Boolean) patch.get("enabled")
                        : existing.getEnabled())
                .description(existing.getDescription())
                .blastRadius(existing.getBlastRadius())
                .seed(existing.getSeed())
                .tags(existing.getTags())
                .build();

        ChaosRuleResponse updated = chaosRuleService.updateRule(org, id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a chaos rule by ID")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("DELETE /api/v1/chaos/rules/{} - Organization: {}", id, org.getSlug());

        chaosRuleService.deleteRule(org, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/target/{target}")
    @Operation(summary = "Delete a chaos rule by target")
    public ResponseEntity<Void> deleteRuleByTarget(@PathVariable String target) {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("DELETE /api/v1/chaos/rules/target/{} - Organization: {}", target, org.getSlug());

        chaosRuleService.deleteRuleByTarget(org, target);
        return ResponseEntity.noContent().build();
    }
}