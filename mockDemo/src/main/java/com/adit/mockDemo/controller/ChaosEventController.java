package com.adit.mockDemo.controller;

import com.adit.mockDemo.dto.*;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.security.TenantContext;
import com.adit.mockDemo.service.ChaosEventService;
import com.adit.mockDemo.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/chaos/events")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chaos Events", description = "Event history, analytics, and webhook management")
@SecurityRequirement(name = "ApiKey")
public class ChaosEventController {

    private final ChaosEventService eventService;
    private final WebhookService    webhookService;
    private final TenantContext     tenantContext;

    // ── Event History ─────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Get chaos event history",
            description = "Paginated list of chaos events. Filter by target, time range, and injection status.")
    public ResponseEntity<PageResponse<ChaosEventResponse>> getEvents(
            @Parameter(description = "Filter by target endpoint") @RequestParam(required = false) String target,
            @Parameter(description = "From timestamp (ISO-8601)") @RequestParam(required = false) Instant from,
            @Parameter(description = "To timestamp (ISO-8601)")   @RequestParam(required = false) Instant to,
            @Parameter(description = "Filter: true=injected only, false=skipped only") @RequestParam(required = false) Boolean injected,
            @Parameter(description = "Page size (max 100)")       @RequestParam(defaultValue = "50") int limit,
            @Parameter(description = "Page number (0-based)")     @RequestParam(defaultValue = "0")  int page) {

        Organization org = tenantContext.getCurrentOrganization();
        log.info("GET /api/v1/chaos/events - Org: {}, target: {}", org.getSlug(), target);

        return ResponseEntity.ok(eventService.getEvents(org, target, from, to, injected, limit, page));
    }

    // ── Analytics ─────────────────────────────────────────────────────────────

    @GetMapping("/analytics")
    @Operation(summary = "Get chaos analytics",
            description = "Aggregated stats: injection rate, top targets, type breakdown, hourly time-series. Window: 1h, 24h, 7d, 30d")
    public ResponseEntity<ChaosAnalyticsResponse> getAnalytics(
            @Parameter(description = "Time window: 1h, 24h, 7d, 30d")
            @RequestParam(defaultValue = "24h") String window) {

        Organization org = tenantContext.getCurrentOrganization();
        log.info("GET /api/v1/chaos/events/analytics?window={} - Org: {}", window, org.getSlug());

        return ResponseEntity.ok(eventService.getAnalytics(org, window));
    }

    // ── Webhooks ──────────────────────────────────────────────────────────────

    @GetMapping("/webhooks")
    @Operation(summary = "List webhook configurations")
    public ResponseEntity<List<WebhookResponse>> getWebhooks() {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("GET /api/v1/chaos/events/webhooks - Org: {}", org.getSlug());
        return ResponseEntity.ok(webhookService.getWebhooks(org));
    }

    @PostMapping("/webhooks")
    @Operation(summary = "Create a webhook",
            description = "Registers a URL to receive chaos injection events. Supports HMAC-SHA256 signing.")
    public ResponseEntity<WebhookResponse> createWebhook(@Valid @RequestBody WebhookRequest request) {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("POST /api/v1/chaos/events/webhooks - Org: {}, name: {}", org.getSlug(), request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(webhookService.createWebhook(org, request));
    }

    @PutMapping("/webhooks/{id}")
    @Operation(summary = "Update a webhook configuration")
    public ResponseEntity<WebhookResponse> updateWebhook(@PathVariable Long id,
                                                         @Valid @RequestBody WebhookRequest request) {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("PUT /api/v1/chaos/events/webhooks/{} - Org: {}", id, org.getSlug());
        return ResponseEntity.ok(webhookService.updateWebhook(org, id, request));
    }

    @DeleteMapping("/webhooks/{id}")
    @Operation(summary = "Delete a webhook configuration")
    public ResponseEntity<Void> deleteWebhook(@PathVariable Long id) {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("DELETE /api/v1/chaos/events/webhooks/{} - Org: {}", id, org.getSlug());
        webhookService.deleteWebhook(org, id);
        return ResponseEntity.noContent().build();
    }
}