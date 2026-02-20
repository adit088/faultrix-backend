package com.adit.mockDemo.integration;

import com.adit.mockDemo.MockDemoApplication;
import com.adit.mockDemo.dto.ChaosRuleRequest;
import com.adit.mockDemo.dto.ChaosRuleResponse;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.repository.ChaosRuleRepository;
import com.adit.mockDemo.repository.OrganizationRepository;
import com.adit.mockDemo.security.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critical security test: Verify that Organization A cannot see Organization B's data.
 * This is the #1 due diligence question for multi-tenant SaaS.
 */
@SpringBootTest(
        classes = MockDemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@Transactional
class MultiTenantIsolationIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrganizationRepository orgRepository;

    @Autowired
    private ChaosRuleRepository ruleRepository;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    private String baseUrl;
    private String orgAApiKey;
    private String orgBApiKey;
    private Organization orgA;
    private Organization orgB;

    @BeforeEach
    void setup() {
        baseUrl = "http://localhost:" + port;

        ruleRepository.deleteAll();
        orgRepository.deleteAll();

        // Create Organization A
        orgAApiKey = "ck_test_org_a_key";
        orgA = Organization.builder()
                .name("Organization A")
                .slug("org-a")
                .apiKey(apiKeyHasher.hash(orgAApiKey))
                .plan("pro")
                .maxRules(50)
                .enabled(true)
                .build();
        orgA = orgRepository.save(orgA);

        // Create Organization B
        orgBApiKey = "ck_test_org_b_key";
        orgB = Organization.builder()
                .name("Organization B")
                .slug("org-b")
                .apiKey(apiKeyHasher.hash(orgBApiKey))
                .plan("pro")
                .maxRules(50)
                .enabled(true)
                .build();
        orgB = orgRepository.save(orgB);
    }

    @Test
    void organizationA_CannotSee_OrganizationBRules() {
        // Org A creates a rule
        ChaosRuleRequest orgARule = new ChaosRuleRequest();
        orgARule.setTarget("/api/v1/payments");
        orgARule.setFailureRate(0.1);
        orgARule.setEnabled(true);

        HttpHeaders headersA = new HttpHeaders();
        headersA.set("X-API-Key", orgAApiKey);
        headersA.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(
                baseUrl + "/api/v1/chaos/rules",
                HttpMethod.POST,
                new HttpEntity<>(orgARule, headersA),
                ChaosRuleResponse.class
        );

        // Org B creates a different rule
        ChaosRuleRequest orgBRule = new ChaosRuleRequest();
        orgBRule.setTarget("/api/v1/orders");
        orgBRule.setFailureRate(0.2);
        orgBRule.setEnabled(true);

        HttpHeaders headersB = new HttpHeaders();
        headersB.set("X-API-Key", orgBApiKey);
        headersB.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(
                baseUrl + "/api/v1/chaos/rules",
                HttpMethod.POST,
                new HttpEntity<>(orgBRule, headersB),
                ChaosRuleResponse.class
        );

        // Org A lists their rules
        ResponseEntity<List<ChaosRuleResponse>> orgAResponse = restTemplate.exchange(
                baseUrl + "/api/v1/chaos/rules",
                HttpMethod.GET,
                new HttpEntity<>(headersA),
                new ParameterizedTypeReference<List<ChaosRuleResponse>>() {}
        );

        assertThat(orgAResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(orgAResponse.getBody()).hasSize(1);
        assertThat(orgAResponse.getBody().get(0).getTarget()).isEqualTo("/api/v1/payments");

        // Org B lists their rules
        ResponseEntity<List<ChaosRuleResponse>> orgBResponse = restTemplate.exchange(
                baseUrl + "/api/v1/chaos/rules",
                HttpMethod.GET,
                new HttpEntity<>(headersB),
                new ParameterizedTypeReference<List<ChaosRuleResponse>>() {}
        );

        assertThat(orgBResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(orgBResponse.getBody()).hasSize(1);
        assertThat(orgBResponse.getBody().get(0).getTarget()).isEqualTo("/api/v1/orders");

        // CRITICAL: Verify Org A's response does NOT contain Org B's rule
        assertThat(orgAResponse.getBody())
                .noneMatch(rule -> rule.getTarget().equals("/api/v1/orders"));

        // CRITICAL: Verify Org B's response does NOT contain Org A's rule
        assertThat(orgBResponse.getBody())
                .noneMatch(rule -> rule.getTarget().equals("/api/v1/payments"));
    }

    @Test
    void organizationA_CannotUpdate_OrganizationBRule() {
        // Org B creates a rule
        ChaosRuleRequest orgBRule = new ChaosRuleRequest();
        orgBRule.setTarget("/api/v1/sensitive");
        orgBRule.setFailureRate(0.1);
        orgBRule.setEnabled(true);

        HttpHeaders headersB = new HttpHeaders();
        headersB.set("X-API-Key", orgBApiKey);
        headersB.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<ChaosRuleResponse> createResponse = restTemplate.exchange(
                baseUrl + "/api/v1/chaos/rules",
                HttpMethod.POST,
                new HttpEntity<>(orgBRule, headersB),
                ChaosRuleResponse.class
        );

        Long orgBRuleId = createResponse.getBody().getId();

        // Org A tries to update Org B's rule (malicious attempt)
        ChaosRuleRequest maliciousUpdate = new ChaosRuleRequest();
        maliciousUpdate.setTarget("/api/v1/hacked");
        maliciousUpdate.setFailureRate(1.0);
        maliciousUpdate.setEnabled(true);

        HttpHeaders headersA = new HttpHeaders();
        headersA.set("X-API-Key", orgAApiKey);
        headersA.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> hackAttempt = restTemplate.exchange(
                baseUrl + "/api/v1/chaos/rules/" + orgBRuleId,
                HttpMethod.PUT,
                new HttpEntity<>(maliciousUpdate, headersA),
                String.class
        );

        // Should be rejected with 404 (rule not found in Org A's scope)
        assertThat(hackAttempt.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Verify Org B's rule was NOT modified
        ResponseEntity<ChaosRuleResponse> verifyResponse = restTemplate.exchange(
                baseUrl + "/api/v1/chaos/rules/" + orgBRuleId,
                HttpMethod.GET,
                new HttpEntity<>(headersB),
                ChaosRuleResponse.class
        );

        assertThat(verifyResponse.getBody().getTarget()).isEqualTo("/api/v1/sensitive");
        assertThat(verifyResponse.getBody().getFailureRate()).isEqualTo(0.1);
    }
}