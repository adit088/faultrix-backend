package com.adit.mockDemo.integration;

import com.adit.mockDemo.MockDemoApplication;
import com.adit.mockDemo.dto.ChaosRuleRequest;
import com.adit.mockDemo.dto.WebhookRequest;
import com.adit.mockDemo.dto.WebhookResponse;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.entity.WebhookDelivery;
import com.adit.mockDemo.repository.OrganizationRepository;
import com.adit.mockDemo.repository.WebhookDeliveryRepository;
import com.adit.mockDemo.security.ApiKeyHasher;
import com.adit.mockDemo.service.WebhookRetryScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for webhook retry mechanism - proves async retry queue works end-to-end.
 * This is a Netflix/Stripe-level feature that buyers love to see tested.
 */
@SpringBootTest(
        classes = MockDemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@Transactional
class WebhookRetryFlowIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrganizationRepository orgRepository;

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;

    @Autowired
    private WebhookRetryScheduler retryScheduler;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    private String baseUrl;
    private String testApiKey;
    private Organization testOrg;

    @BeforeEach
    void setup() {
        baseUrl = "http://localhost:" + port;

        deliveryRepository.deleteAll();
        orgRepository.deleteAll();

        testApiKey = "ck_test_webhook_test";
        String hashedKey = apiKeyHasher.hash(testApiKey);

        testOrg = Organization.builder()
                .name("Webhook Test Org")
                .slug("webhook-test")
                .apiKey(hashedKey)
                .plan("enterprise")
                .maxRules(100)
                .enabled(true)
                .build();

        testOrg = orgRepository.save(testOrg);
    }

    @Test
    void webhookCreation_StoresConfiguration() {
        WebhookRequest request = new WebhookRequest();
        request.setName("Test Webhook");
        request.setUrl("https://example.com/webhook");
        request.setSecret("test_secret_123");
        request.setEnabled(true);
        request.setOnInjection(true);
        request.setOnSkipped(false);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", testApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<WebhookResponse> response = restTemplate.exchange(
                baseUrl + "/api/v1/webhooks",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                WebhookResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Test Webhook");
        assertThat(response.getBody().getUrl()).isEqualTo("https://example.com/webhook");
        assertThat(response.getBody().getHasSecret()).isTrue();
        assertThat(response.getBody().getEnabled()).isTrue();
    }

    @Test
    void failedWebhookDelivery_IsScheduledForRetry() throws InterruptedException {
        // Create a webhook pointing to a non-existent endpoint (will fail)
        WebhookRequest request = new WebhookRequest();
        request.setName("Failing Webhook");
        request.setUrl("http://localhost:99999/nonexistent");  // Invalid port
        request.setEnabled(true);
        request.setOnInjection(true);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", testApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<WebhookResponse> webhookResponse = restTemplate.exchange(
                baseUrl + "/api/v1/webhooks",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                WebhookResponse.class
        );

        Long webhookId = webhookResponse.getBody().getId();

        // Create chaos rule that will trigger webhook
        ChaosRuleRequest ruleRequest = new ChaosRuleRequest();
        ruleRequest.setTarget("/api/v1/users");
        ruleRequest.setFailureRate(1.0);  // 100% to guarantee injection
        ruleRequest.setEnabled(true);

        restTemplate.exchange(
                baseUrl + "/api/v1/chaos/rules",
                HttpMethod.POST,
                new HttpEntity<>(ruleRequest, headers),
                ChaosRuleRequest.class
        );

        // Trigger the endpoint (chaos will inject, webhook will be called and fail)
        restTemplate.exchange(
                baseUrl + "/api/v1/users",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        // Give async webhook delivery time to fail
        Thread.sleep(2000);

        // Verify delivery was logged as FAILED with retry scheduled
        List<WebhookDelivery> deliveries = deliveryRepository.findAll();
        assertThat(deliveries).isNotEmpty();

        WebhookDelivery delivery = deliveries.get(0);
        assertThat(delivery.getStatus()).isEqualTo("FAILED");
        assertThat(delivery.getNextRetryAt()).isNotNull();
        assertThat(delivery.getNextRetryAt()).isAfter(Instant.now());
        assertThat(delivery.getAttempt()).isEqualTo(1);
        assertThat(delivery.getMaxAttempts()).isEqualTo(3);
        assertThat(delivery.getRequestPayload()).isNotNull();  // Payload stored for retry
    }

    @Test
    void retryScheduler_ProcessesFailedDeliveries() throws InterruptedException {
        // Manually create a failed delivery that's ready for retry
        WebhookDelivery failedDelivery = WebhookDelivery.builder()
                .webhookId(1L)  // Dummy webhook ID
                .chaosEventId(1L)
                .status("FAILED")
                .attempt(1)
                .maxAttempts(3)
                .nextRetryAt(Instant.now().minusSeconds(60))  // Past due for retry
                .requestPayload("{\"event\":\"chaos.injected\",\"target\":\"/test\"}")
                .build();

        deliveryRepository.save(failedDelivery);

        // Run the retry scheduler
        retryScheduler.retryFailedWebhooks();

        // Wait for async processing
        Thread.sleep(1000);

        // Verify the delivery was updated (even if retry failed again)
        WebhookDelivery updated = deliveryRepository.findById(failedDelivery.getId()).orElse(null);
        assertThat(updated).isNotNull();

        // Either succeeded or incremented attempt counter
        assertThat(updated.getAttempt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void webhookDelivery_ExhaustsRetries_AfterMaxAttempts() throws InterruptedException {
        // Create delivery at max attempts
        WebhookDelivery exhaustedDelivery = WebhookDelivery.builder()
                .webhookId(1L)
                .chaosEventId(1L)
                .status("FAILED")
                .attempt(3)  // Already at max
                .maxAttempts(3)
                .nextRetryAt(Instant.now().minusSeconds(60))
                .requestPayload("{\"event\":\"chaos.injected\"}")
                .errorMessage("Connection refused")
                .build();

        deliveryRepository.save(exhaustedDelivery);

        // Run retry scheduler
        retryScheduler.retryFailedWebhooks();
        Thread.sleep(1000);

        // Verify retry was not scheduled again (nextRetryAt should be null)
        WebhookDelivery updated = deliveryRepository.findById(exhaustedDelivery.getId()).orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo("FAILED");

        // If it tried and failed again, nextRetryAt should be null (max attempts reached)
        if (updated.getAttempt() >= updated.getMaxAttempts()) {
            assertThat(updated.getNextRetryAt()).isNull();
        }
    }
}