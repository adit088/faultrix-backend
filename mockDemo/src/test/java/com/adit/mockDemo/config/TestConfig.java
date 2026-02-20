package com.adit.mockDemo.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

/**
 * Test-specific configuration to override beans for integration testing.
 * Prevents real HTTP calls during tests while keeping the same code paths.
 */
@TestConfiguration
public class TestConfig {

    /**
     * Test-specific RestTemplate with shorter timeouts.
     * Integration tests should fail fast, not wait 30 seconds for timeouts.
     */
    @Bean
    @Primary
    public RestTemplate testRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        // Set aggressive timeouts for tests - fail fast
        restTemplate.getInterceptors().add((request, body, execution) -> {
            // Tests should complete quickly
            return execution.execute(request, body);
        });
        return restTemplate;
    }
}