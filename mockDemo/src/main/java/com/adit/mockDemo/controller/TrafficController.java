package com.adit.mockDemo.controller;

import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.metrics.ChaosMetrics;
import com.adit.mockDemo.security.TenantContext;
import com.adit.mockDemo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/v1/experiments")
@Slf4j
@Tag(name = "Chaos Experiments", description = "Run chaos engineering experiments")
@SecurityRequirement(name = "ApiKey")
public class TrafficController {

    private final UserService   userService;
    private final ChaosMetrics  metrics;
    private final TenantContext tenantContext;
    private final Executor      executor;

    public TrafficController(UserService userService,
                             ChaosMetrics metrics,
                             TenantContext tenantContext,
                             @Qualifier("chaosAsyncExecutor") Executor executor) {
        this.userService   = userService;
        this.metrics       = metrics;
        this.tenantContext = tenantContext;
        this.executor      = executor;
    }

    /**
     * Returns current experiment traffic stats as JSON.
     * This is what the dashboard Experiments page polls every 10 seconds.
     * Returns: { total, injected, skipped, injectionRate }
     */
    @GetMapping("/traffic")
    @Operation(
            summary = "Get traffic stats",
            description = "Returns current experiment metrics: total requests, injected failures, " +
                    "skipped requests, and injection rate. Poll this to track live experiment state."
    )
    public ResponseEntity<Map<String, Object>> getTrafficStats() {
        Organization org = tenantContext.getCurrentOrganization();
        log.info("GET /api/v1/experiments/traffic - Org: {}", org.getSlug());

        long total    = metrics.getCurrentTraffic();
        long injected = metrics.getExperimentFailures();
        long skipped  = Math.max(0, total - injected);
        double rate   = total > 0 ? (double) injected / total : 0.0;

        Map<String, Object> stats = Map.of(
                "total",         total,
                "injected",      injected,
                "skipped",       skipped,
                "injectionRate", rate
        );

        return ResponseEntity.ok(stats);
    }

    /**
     * Simulate N concurrent user requests to generate chaos events.
     * This triggers actual traffic — use POST to make intent clear.
     */
    @PostMapping("/traffic/simulate")
    @Operation(
            summary = "Simulate traffic",
            description = "Simulate N concurrent user requests against the UserController endpoint " +
                    "to generate chaos events for analysis. Requests run in parallel using the " +
                    "chaos async executor."
    )
    public ResponseEntity<String> simulateTraffic(
            @Parameter(description = "Number of simulated users (1-500)")
            @RequestParam(defaultValue = "10") int users) {

        if (users < 1 || users > 500) {
            return ResponseEntity.badRequest()
                    .body("users must be between 1 and 500");
        }

        Organization org = tenantContext.getCurrentOrganization();
        log.info("POST /api/v1/experiments/traffic/simulate - Org: {}, Users: {} (parallel)",
                org.getSlug(), users);

        metrics.resetExperiment();
        metrics.setCurrentTraffic(users);

        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger failed    = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = IntStream.range(0, users)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        userService.getAllUsers();
                        succeeded.incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        metrics.recordFailure();
                        log.debug("Simulated request {} failed (expected during chaos): {}",
                                i + 1, e.getMessage());
                    }
                }, executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        int s = succeeded.get();
        int f = failed.get();
        log.info("Traffic simulation completed: {} succeeded, {} failed", s, f);
        return ResponseEntity.ok(String.format(
                "Simulated %d users — %d succeeded, %d failed (chaos injected)",
                users, s, f
        ));
    }
}