package com.adit.mockDemo.config;

import com.adit.mockDemo.security.ApiKeyAuthFilter;
import com.adit.mockDemo.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyAuthFilter apiKeyAuthFilter;
    private final RateLimitFilter  rateLimitFilter;

    /**
     * API key authentication runs FIRST.
     * It resolves the Organization from the API key and sets it as a request attribute.
     * RateLimitFilter depends on that attribute — so auth must precede rate limiting.
     */
    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyFilterRegistration() {
        FilterRegistrationBean<ApiKeyAuthFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(apiKeyAuthFilter);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);       // runs first
        return reg;
    }

    /**
     * Rate limit filter runs AFTER authentication.
     * By this point, request.getAttribute("currentOrganization") is populated,
     * so per-org sliding window rate limiting actually works.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration() {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(rateLimitFilter);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);   // runs after auth
        return reg;
    }
}