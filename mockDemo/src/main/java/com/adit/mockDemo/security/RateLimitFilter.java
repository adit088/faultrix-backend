package com.adit.mockDemo.security;

import com.adit.mockDemo.entity.Organization;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-tenant rate limiting filter (no external dependencies).
 * Uses sliding window algorithm with synchronized per-org queues for thread safety.
 *
 * FIX SEC-4: /actuator/prometheus removed from public bypass list.
 * Prometheus scraping now requires a valid API key (or restrict at network/infra level).
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<Long, Deque<Instant>> requestWindows = new ConcurrentHashMap<>();

    private static final Map<String, Integer> PLAN_LIMITS = Map.of(
            "free",         60,
            "starter",      300,
            "professional", 1200,
            "enterprise",   10000
    );

    private static final long WINDOW_SIZE_MS = 60_000L;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Organization org = (Organization) request.getAttribute("currentOrganization");

        if (org == null) {
            // Not authenticated — let ApiKeyAuthFilter handle the 401
            filterChain.doFilter(request, response);
            return;
        }

        if (isRateLimitExceeded(org)) {
            log.warn("Rate limit exceeded for organization: {} (plan: {})", org.getSlug(), org.getPlan());
            sendRateLimitExceeded(response, org);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimitExceeded(Organization org) {
        int limit = PLAN_LIMITS.getOrDefault(org.getPlan().toLowerCase(), 60);

        Deque<Instant> queue = requestWindows.computeIfAbsent(org.getId(), k -> new ArrayDeque<>());

        Instant now         = Instant.now();
        Instant windowStart = now.minusMillis(WINDOW_SIZE_MS);

        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst().isBefore(windowStart)) {
                queue.pollFirst();
            }
            if (queue.size() >= limit) {
                return true;
            }
            queue.addLast(now);
            return false;
        }
    }

    /**
     * FIX SEC-4: /actuator/prometheus intentionally REMOVED from this list.
     * Matches PUBLIC_PREFIXES in ApiKeyAuthFilter exactly.
     */
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/swagger-ui")    ||
                path.startsWith("/v3/api-docs")   ||
                path.startsWith("/h2-console")    ||
                path.startsWith("/actuator/health") ||
                path.startsWith("/api/v1/system") ||
                path.startsWith("/api/v1/auth")   ||
                path.equals("/");
        // /actuator/prometheus intentionally absent (SEC-4)
    }

    private void sendRateLimitExceeded(HttpServletResponse response, Organization org) throws IOException {
        int limit = PLAN_LIMITS.getOrDefault(org.getPlan().toLowerCase(), 60);

        response.setStatus(429);
        response.setContentType("application/json");
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setHeader("Retry-After", "60");

        response.getWriter().write(String.format(
                "{\"errorCode\":\"RATE_LIMIT_EXCEEDED\"," +
                        "\"message\":\"Rate limit exceeded. Your %s plan allows %d requests per minute. Upgrade for higher limits.\"," +
                        "\"status\":429," +
                        "\"limit\":%d," +
                        "\"retryAfter\":60}",
                org.getPlan(), limit, limit
        ));
    }

    /**
     * Called by ScheduledTasks every 5 minutes to prevent memory leaks.
     */
    public void cleanup() {
        Instant cutoff = Instant.now().minusMillis(WINDOW_SIZE_MS * 2);

        requestWindows.forEach((orgId, queue) -> {
            synchronized (queue) {
                while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) {
                    queue.pollFirst();
                }
            }
        });

        requestWindows.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty();
            }
        });

        log.debug("Rate limiter cleanup completed. Active orgs: {}", requestWindows.size());
    }
}