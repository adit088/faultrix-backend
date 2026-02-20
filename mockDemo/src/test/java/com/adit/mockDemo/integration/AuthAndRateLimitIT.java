package com.adit.mockDemo.integration;

import com.adit.mockDemo.MockDemoApplication;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.repository.OrganizationRepository;
import com.adit.mockDemo.security.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security integration tests: API key auth + rate limiting work end-to-end
 */
@SpringBootTest(
        classes = MockDemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@Transactional
class AuthAndRateLimitIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrganizationRepository orgRepository;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    private String baseUrl;

    @BeforeEach
    void setup() {
        baseUrl = "http://localhost:" + port;
        orgRepository.deleteAll();
    }

    @Test
    void requestWithoutApiKey_IsRejected() {
        HttpHeaders headers = new HttpHeaders();
        // NO X-API-Key header

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/v1/chaos/rules",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Missing API key");
    }

    @Test
    void requestWithInvalidApiKey_IsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "ck_test_invalid_key_12345");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/v1/chaos/rules",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid API key");
    }

    @Test
    void requestWithValidApiKey_IsAllowed() {
        // Create org with valid key
        String validKey = "ck_test_valid_key";
        Organization org = Organization.builder()
                .name("Valid Org")
                .slug("valid-org")
                .apiKey(apiKeyHasher.hash(validKey))
                .plan("free")
                .maxRules(10)
                .enabled(true)
                .build();
        orgRepository.save(org);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", validKey);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/v1/chaos/rules",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void disabledOrganization_IsRejected() {
        // Create DISABLED org
        String validKey = "ck_test_disabled_org";
        Organization org = Organization.builder()
                .name("Disabled Org")
                .slug("disabled-org")
                .apiKey(apiKeyHasher.hash(validKey))
                .plan("free")
                .maxRules(10)
                .enabled(false)  // DISABLED
                .build();
        orgRepository.save(org);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", validKey);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/v1/chaos/rules",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Organization is disabled");
    }

    @Test
    void rateLimitEnforcement_FreePlan_60RequestsPerMinute() {
        // Create free plan org (60 req/min limit)
        String apiKey = "ck_test_rate_limit";
        Organization org = Organization.builder()
                .name("Rate Limited Org")
                .slug("rate-limited")
                .apiKey(apiKeyHasher.hash(apiKey))
                .plan("free")  // 60 req/min
                .maxRules(10)
                .enabled(true)
                .build();
        orgRepository.save(org);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);

        // Send 60 requests - all should succeed
        for (int i = 0; i < 60; i++) {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/api/v1/chaos/rules",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // 61st request should be rate limited
        ResponseEntity<String> rateLimitedResponse = restTemplate.exchange(
                baseUrl + "/api/v1/chaos/rules",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(rateLimitedResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rateLimitedResponse.getBody()).contains("Rate limit exceeded");
        assertThat(rateLimitedResponse.getHeaders().containsKey("X-RateLimit-Limit")).isTrue();
        assertThat(rateLimitedResponse.getHeaders().containsKey("Retry-After")).isTrue();
    }

    @Test
    void publicEndpoints_BypassAuth() {
        // Health check should work without API key
        ResponseEntity<String> healthResponse = restTemplate.getForEntity(
                baseUrl + "/actuator/health",
                String.class
        );

        assertThat(healthResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Prometheus should work without API key
        ResponseEntity<String> metricsResponse = restTemplate.getForEntity(
                baseUrl + "/actuator/prometheus",
                String.class
        );

        assertThat(metricsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}