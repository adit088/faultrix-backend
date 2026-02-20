package com.adit.mockDemo.insights;

import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/insights")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Chaos Insights", description = "AI-powered insights from chaos experiments")
@SecurityRequirement(name = "ApiKey")
public class InsightController {

    private final InsightEngine insightEngine;
    private final TenantContext tenantContext;

    @GetMapping
    @Operation(
            summary = "Get AI insights for target",
            description = "Analyzes last 1000 chaos events for the given target and generates actionable insights. " +
                    "Pass target as a query param to support paths containing slashes (e.g. /api/v1/users)."
    )
    public List<FailureInsight> getInsights(
            @Parameter(description = "Target endpoint or service name (e.g. /api/v1/users or user-service)")
            @RequestParam String target) {

        Organization org = tenantContext.getCurrentOrganization();
        log.info("GET /api/v1/insights?target={} - Org: {}", target, org.getSlug());

        return insightEngine.generateInsights(target, org);
    }
}