package com.adit.mockDemo.proxy;

import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP Chaos Proxy — Faultrix's zero-SDK integration point.
 *
 * Instead of installing an SDK, developers route their outbound HTTP calls
 * through this endpoint. Faultrix injects chaos based on their rules,
 * logs events for analytics, and forwards the request to the real upstream.
 *
 * Integration is ONE env var change:
 *
 *   Before:  PAYMENT_API_URL=https://api.stripe.com
 *   After:   Route calls through POST /api/v1/proxy/forward
 *
 * Works with ANY language — Node.js, Python, Go, Ruby, etc.
 * No SDK. No agent. No code changes beyond one HTTP call wrapper.
 */
@RestController
@RequestMapping("/api/v1/proxy")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Chaos Proxy", description = "Zero-SDK chaos injection proxy — route outbound HTTP calls through Faultrix")
@SecurityRequirement(name = "ApiKey")
public class ProxyController {

    private final ProxyChaosService proxyChaosService;
    private final TenantContext     tenantContext;

    @PostMapping("/forward")
    @Operation(
            summary = "Forward HTTP request through Faultrix chaos proxy",
            description = """
                    Route any outbound HTTP call through this endpoint.
                    Faultrix will:
                    1. Match the request URL path against your chaos rules
                    2. Decide whether to inject chaos (based on rule config, schedule, blast radius)
                    3. Log the event for analytics and insights
                    4. Either return a chaos error response OR forward to the real upstream
                    
                    Response always includes X-Faultrix-* headers with chaos metadata.
                    
                    Integration example (any language):
                    ```
                    # Instead of calling Stripe directly:
                    POST https://faultrix-backend.railway.app/api/v1/proxy/forward
                    X-API-Key: your-key
                    {
                      "method": "GET",
                      "url": "https://api.stripe.com/v1/charges",
                      "headers": { "Authorization": "Bearer sk_live_xxx" }
                    }
                    ```
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Request processed (chaos may or may not have been injected)"),
            @ApiResponse(responseCode = "400", description = "Invalid request — missing method or url"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key"),
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                    schema = @Schema(implementation = ProxyRequest.class),
                    examples = @ExampleObject(
                            name = "Stripe API call",
                            value = """
                                    {
                                      "method": "GET",
                                      "url": "https://api.stripe.com/v1/charges",
                                      "headers": {
                                        "Authorization": "Bearer sk_live_xxx",
                                        "Content-Type": "application/json"
                                      }
                                    }
                                    """
                    )
            )
    )
    public ResponseEntity<ProxyResponse> forward(@Valid @RequestBody ProxyRequest request) {
        Organization org = tenantContext.getCurrentOrganization();

        log.info("PROXY FORWARD — Org: {}, Method: {}, URL: {}",
                org.getSlug(), request.getMethod(), request.getUrl());

        ProxyResponse response = proxyChaosService.process(org, request);

        return ResponseEntity
                .status(response.getStatus())
                .body(response);
    }

    @GetMapping("/health")
    @Operation(summary = "Proxy health check", description = "Verify the chaos proxy is reachable and authenticated")
    public ResponseEntity<ProxyHealthResponse> health() {
        Organization org = tenantContext.getCurrentOrganization();
        return ResponseEntity.ok(ProxyHealthResponse.builder()
                .status("UP")
                .organization(org.getSlug())
                .message("Faultrix chaos proxy is ready. Route your HTTP calls through POST /api/v1/proxy/forward")
                .build());
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    @lombok.Getter
    @lombok.Builder
    static class ProxyHealthResponse {
        private String status;
        private String organization;
        private String message;
    }
}