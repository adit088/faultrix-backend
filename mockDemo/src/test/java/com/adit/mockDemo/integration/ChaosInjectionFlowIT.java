package com.adit.mockDemo.integration;

import com.adit.mockDemo.MockDemoApplication;
import com.adit.mockDemo.dto.ChaosRuleRequest;
import com.adit.mockDemo.dto.ChaosRuleResponse;
import com.adit.mockDemo.entity.ChaosEvent;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.repository.ChaosEventRepository;
import com.adit.mockDemo.repository.ChaosRuleRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test: Create chaos rule → Trigger chaos → Verify event logged
 * This proves the entire chaos injection pipeline works in production-like conditions.
 */
@SpringBootTest(
        classes = MockDemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@Transactional
class ChaosInjectionFlowIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrganizationRepository orgRepository;

    @Autowired
    private ChaosRuleRepository ruleRepository;

    @Autowired
    private ChaosEventRepository eventRepository;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    private String baseUrl;
    private String testApiKey;
    private Organization testOrg;

    @BeforeEach
    void setup() {
        baseUrl = "http://localhost:" + port;

        // Clean slate
        eventRepository.deleteAll();
        ruleRepository.deleteAll();
        orgRepository.deleteAll();

        // Create test organization with known API key
        testApiKey = "ck_test_integration_12345";
        String hashedKey = apiKeyHasher.hash(testApiKey);

        testOrg = Organization.builder()
                .name("Integration Test Org")
                .slug("integration-test")
                .apiKey(hashedKey)
                .plan("enterprise")
                .maxRules(100)
                .enabled(true)
                .build();

        testOrg = orgRepository.save(testOrg);
    }

    @Test
    void fullChaosInjectionFlow_CreatesRuleAndLogsEvent() throws InterruptedException {
        // STEP 1: Create a chaos rule via API
        ChaosRuleRequest ruleRequest = new ChaosRuleRequest();
        ruleRequest.setTarget("/api/v1/users");
        ruleRequest.setFailureRate(1.0);  // 100% failure for predictable test
        ruleRequest.setMaxDelayMs(100L);
        ruleRequest.setEnabled(true);
        ruleRequest.setDescription("Integration test rule");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", testApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ChaosRuleRequest> createRequest = new HttpEntity<>(ruleRequest, headers);

        ResponseEntity<ChaosRuleResponse> createResponse = restTemplate.exchange(
                baseUrl + "/api/v1/chaos/rules",
                HttpMethod.POST,
                createRequest,
                ChaosRuleResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().getTarget()).isEqualTo("/api/v1/users");

        // STEP 2: Trigger the endpoint that should get chaos injected
        HttpEntity<Void> triggerRequest = new HttpEntity<>(headers);

        ResponseEntity<String> chaosResponse = restTemplate.exchange(
                baseUrl + "/api/v1/users",
                HttpMethod.GET,
                triggerRequest,
                String.class
        );

        // Chaos should have been injected (100% failure rate)
        assertThat(chaosResponse.getStatusCode().is4xxClientError() ||
                chaosResponse.getStatusCode().is5xxServerError()).isTrue();

        // STEP 3: Verify chaos event was logged to database
        // Give async processing a moment to complete
        Thread.sleep(500);

        List<ChaosEvent> events = eventRepository.findAll();
        assertThat(events).isNotEmpty();

        ChaosEvent event = events.get(0);
        assertThat(event.getOrganization().getId()).isEqualTo(testOrg.getId());
        assertThat(event.getTarget()).isEqualTo("/api/v1/users");
        assertThat(event.getInjected()).isTrue();
        assertThat(event.getHttpStatus()).isNotNull();
    }

    @Test
    void chaosRule_WhenDisabled_DoesNotInjectChaos() throws InterruptedException {
        // Create DISABLED chaos rule
        ChaosRuleRequest ruleRequest = new ChaosRuleRequest();
        ruleRequest.setTarget("/api/v1/users");
        ruleRequest.setFailureRate(1.0);
        ruleRequest.setEnabled(false);  // DISABLED

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", testApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ChaosRuleRequest> createRequest = new HttpEntity<>(ruleRequest, headers);

        restTemplate.exchange(
                baseUrl + "/api/v1/chaos/rules",
                HttpMethod.POST,
                createRequest,
                ChaosRuleResponse.class
        );

        // Trigger endpoint
        HttpEntity<Void> triggerRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/v1/users",
                HttpMethod.GET,
                triggerRequest,
                String.class
        );

        // Should succeed (no chaos injected)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Thread.sleep(500);

        // Verify no injection event (only skipped events if any)
        List<ChaosEvent> events = eventRepository.findAll();
        if (!events.isEmpty()) {
            assertThat(events.get(0).getInjected()).isFalse();
        }
    }
}