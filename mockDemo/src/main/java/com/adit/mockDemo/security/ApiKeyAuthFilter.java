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

@Component
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final OrganizationRepository organizationRepository;
    private final ApiKeyHasher           apiKeyHasher;
    private final Counter                apiAuthFailureCounter;

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String ORG_ATTRIBUTE  = "currentOrganization";

    // All paths that bypass auth — must be kept minimal and explicit
    private static final String[] PUBLIC_PREFIXES = {
            "/swagger-ui",
            "/v3/api-docs",
            "/h2-console",
            "/actuator/health",
            "/actuator/prometheus",
            "/api/v1/system"
    };

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

        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (rawKey == null || rawKey.trim().isEmpty()) {
            log.warn("Missing API key for path: {}", path);
            sendUnauthorized(response, "Missing API key. Include 'X-API-Key' header.");
            return;
        }

        // Hash the incoming raw key before DB lookup — DB stores only hashes
        String hashedKey = apiKeyHasher.hash(rawKey);
        Organization org = organizationRepository.findByApiKey(hashedKey).orElse(null);

        if (org == null) {
            apiAuthFailureCounter.increment();
            // Log only the prefix of the raw key — never log the full key
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

        // Set org on request — RateLimitFilter reads this downstream
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
}