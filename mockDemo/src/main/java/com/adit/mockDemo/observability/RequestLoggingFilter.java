package com.adit.mockDemo.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component
@Order(2)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip logging for health checks and static resources
        String path = request.getRequestURI();
        if (shouldSkipLogging(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // Build structured log
            logRequest(wrappedRequest, wrappedResponse, duration);

            // Copy response body back
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request,
                            ContentCachingResponseWrapper response,
                            long duration) {

        String method = request.getMethod();
        String path = request.getRequestURI();
        String query = request.getQueryString();
        int status = response.getStatus();
        String userAgent = request.getHeader("User-Agent");
        String organization = MDC.get("organization");

        // Structured logging (JSON-friendly)
        log.info("HTTP {} {} {} - status={} duration={}ms org={} ua={}",
                method,
                path,
                query != null ? "?" + query : "",
                status,
                duration,
                organization != null ? organization : "unknown",
                userAgent != null ? userAgent.substring(0, Math.min(50, userAgent.length())) : "unknown");

        // Warn on slow requests
        if (duration > 1000) {
            log.warn("SLOW REQUEST: {} {} took {}ms", method, path, duration);
        }

        // Error logging
        if (status >= 500) {
            log.error("SERVER ERROR: {} {} returned {}", method, path, status);
        } else if (status >= 400) {
            log.warn("CLIENT ERROR: {} {} returned {}", method, path, status);
        }
    }

    private boolean shouldSkipLogging(String path) {
        return path.startsWith("/actuator/health") ||
                path.startsWith("/actuator/prometheus") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/favicon.ico");
    }
}