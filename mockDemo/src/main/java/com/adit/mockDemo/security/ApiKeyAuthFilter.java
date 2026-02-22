package com.adit.mockDemo.security;

import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.repository.OrganizationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final OrganizationRepository organizationRepository;
    private final ApiKeyHasher           apiKeyHasher;
    private final Counter                apiAuthFailureCounter;

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String ORG_ATTRIBUTE  = "currentOrganization";

    /**
     * SEC-4: /actuator/prometheus REMOVED from public prefixes.
     * SEC-8: Auth endpoints are public but rate-limited by IP.
     */
    private static final String[] PUBLIC_PREFIXES = {
            "/swagger-ui",
            "/v3/api-docs",
            "/h2-console",
            "/actuator/health",
            "/api/v1/system",
            "/api/v1/auth"
    };

    // SEC-8: IP-based rate limiting for auth endpoints — 10 req/min per IP.
    // NOTE: We use request.getRemoteAddr() directly — NOT X-Forwarded-For.
    // X-Forwarded-For can be spoofed by any client; remoteAddr is set by the
    // TCP layer and cannot be forged by the client. When deployed behind a
    // trusted reverse proxy (Railway, nginx), Spring's ForwardedHeaderFilter
    // (enabled via server.forward-headers-strategy=NATIVE in application-prod.yml)
    // correctly rewrites remoteAddr to the real client IP from the proxy's
    // X-Forwarded-For — but only when the request originates from the proxy's
    // own IP, making it safe.
    private static final int  AUTH_RATE_LIMIT_PER_MIN = 10;
    private static final long AUTH_WINDOW_MS           = 60_000L;

    // Memory: bounded by number of unique IPs × ~200 bytes per entry.
    // Cleaned up by RateLimitFilter.cleanup() every 5 min.
    private final Map<String, Deque<Instant>> authIpWindows = new ConcurrentHashMap<>();

    public ApiKeyAuthFilter(OrganizationRepository organizationRepository,
                            ApiKeyHasher apiKeyHasher,
                            MeterRegistry meterRegistry) {
        this.organizationRepository = organizationRepository;
        this.apiKeyHasher = apiKeyHasher;
        this.apiAuthFailureCounter = Counter.builder("chaoslab.api.auth.failures")
                .description("Failed API authentication attempts")
                .tag("application", "chaoslab")
                .register(meterRegistry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path   = request.getRequestURI();
        String rawKey = request.getHeader(API_KEY_HEADER);

        // Rate-limit auth endpoints by IP before allowing through.
        // Uses remoteAddr — safe because ForwardedHeaderFilter (NATIVE strategy)
        // has already rewritten it to the real client IP from the trusted proxy.
        if (path.startsWith("/api/v1/auth")) {
            String ip = request.getRemoteAddr();
            if (isAuthRateLimitExceeded(ip)) {
                log.warn("Auth rate limit exceeded for IP: {}", ip);
                sendTooManyRequests(response);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (rawKey == null || rawKey.trim().isEmpty()) {
            log.warn("Missing API key for path: {}", path);
            sendUnauthorized(response, "Missing API key. Include 'X-API-Key' header.");
            return;
        }

        // Validate key length to prevent DoS via absurdly long keys
        if (rawKey.length() > 200) {
            apiAuthFailureCounter.increment();
            log.warn("Oversized API key rejected (length: {})", rawKey.length());
            sendUnauthorized(response, "Invalid API key");
            return;
        }

        String hashedKey = apiKeyHasher.hash(rawKey.trim());
        Organization org = organizationRepository.findByApiKey(hashedKey).orElse(null);

        if (org == null) {
            apiAuthFailureCounter.increment();
            log.warn("Invalid API key attempted (prefix: {}...)",
                    rawKey.substring(0, Math.min(10, rawKey.length())));
            sendUnauthorized(response, "Invalid API key");
            return;
        }

        if (!org.getEnabled()) {
            apiAuthFailureCounter.increment();
            log.warn("Disabled organization attempted access: {}", org.getSlug());
            sendUnauthorized(response, "Organization is disabled. Contact support.");
            return;
        }

        request.setAttribute(ORG_ATTRIBUTE, org);
        MDC.put("organization", org.getSlug());
        MDC.put("organizationId", org.getId().toString());
        log.debug("Authenticated request for organization: {}", org.getSlug());

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("organization");
            MDC.remove("organizationId");
        }
    }

    // ── Auth IP rate limiter ──────────────────────────────────────────────────

    private boolean isAuthRateLimitExceeded(String ip) {
        Deque<Instant> queue = authIpWindows.computeIfAbsent(ip, k -> new ArrayDeque<>());
        Instant now         = Instant.now();
        Instant windowStart = now.minusMillis(AUTH_WINDOW_MS);

        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst().isBefore(windowStart)) {
                queue.pollFirst();
            }
            if (queue.size() >= AUTH_RATE_LIMIT_PER_MIN) {
                return true;
            }
            queue.addLast(now);
            return false;
        }
    }

    /**
     * Clean up stale auth IP windows.
     * Called by ScheduledTasks every 5 minutes to prevent memory leaks.
     */
    public void cleanupAuthWindows() {
        Instant cutoff = Instant.now().minusMillis(AUTH_WINDOW_MS * 2);
        authIpWindows.forEach((ip, queue) -> {
            synchronized (queue) {
                while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) {
                    queue.pollFirst();
                }
            }
        });
        authIpWindows.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty();
            }
        });
        log.debug("Auth IP window cleanup completed. Active IPs: {}", authIpWindows.size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isPublicEndpoint(String path) {
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return path.equals("/");
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"errorCode\":\"UNAUTHORIZED\",\"message\":\"%s\",\"status\":401}",
                message
        ));
    }

    private void sendTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setHeader("Retry-After", "60");
        response.getWriter().write(
                "{\"errorCode\":\"RATE_LIMIT_EXCEEDED\"," +
                        "\"message\":\"Too many auth requests. Try again in 60 seconds.\"," +
                        "\"status\":429}"
        );
    }
}