package com.adit.mockDemo.service;

import com.adit.mockDemo.chaos.execution.ChaosDecision;
import com.adit.mockDemo.dto.WebhookRequest;
import com.adit.mockDemo.dto.WebhookResponse;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.entity.WebhookConfig;
import com.adit.mockDemo.entity.WebhookDelivery;
import com.adit.mockDemo.exception.ResourceNotFoundException;
import com.adit.mockDemo.repository.WebhookConfigRepository;
import com.adit.mockDemo.repository.WebhookDeliveryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WebhookService {

    private final WebhookConfigRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public WebhookResponse createWebhook(Organization org, WebhookRequest request) {
        log.info("POST /webhooks - Creating webhook '{}' for org: {}", request.getName(), org.getSlug());

        WebhookConfig config = WebhookConfig.builder()
                .organization(org)
                .name(request.getName())
                .url(request.getUrl())
                .secret(request.getSecret())
                .enabled(request.getEnabled())
                .onInjection(request.getOnInjection())
                .onSkipped(request.getOnSkipped())
                .chaosTypes(request.getChaosTypes())
                .build();

        return mapToResponse(webhookRepository.save(config));
    }

    @Transactional(readOnly = true)
    public List<WebhookResponse> getWebhooks(Organization org) {
        log.info("GET /webhooks - Org: {}", org.getSlug());
        return webhookRepository.findByOrganization(org)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public WebhookResponse updateWebhook(Organization org, Long id, WebhookRequest request) {
        log.info("PUT /webhooks/{} - Org: {}", id, org.getSlug());

        WebhookConfig config = webhookRepository.findById(id)
                .filter(w -> w.getOrganization().getId().equals(org.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("WebhookConfig", id.toString()));

        config.setName(request.getName());
        config.setUrl(request.getUrl());
        if (request.getSecret() != null) config.setSecret(request.getSecret());
        config.setEnabled(request.getEnabled());
        config.setOnInjection(request.getOnInjection());
        config.setOnSkipped(request.getOnSkipped());
        config.setChaosTypes(request.getChaosTypes());

        return mapToResponse(webhookRepository.save(config));
    }

    public void deleteWebhook(Organization org, Long id) {
        log.info("DELETE /webhooks/{} - Org: {}", id, org.getSlug());

        WebhookConfig config = webhookRepository.findById(id)
                .filter(w -> w.getOrganization().getId().equals(org.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("WebhookConfig", id.toString()));

        webhookRepository.delete(config);
    }

    // ── Delivery with retry tracking ─────────────────────────────────────────

    @Async("chaosAsyncExecutor")
    public void fireInjectionWebhooks(Organization org,
                                      String target,
                                      String requestId,
                                      ChaosDecision decision) {
        try {
            List<WebhookConfig> webhooks = webhookRepository.findActiveForInjection(org);

            if (webhooks.isEmpty()) return;

            Map<String, Object> payload = buildPayload(org, target, requestId, decision);

            webhooks.forEach(webhook -> {
                if (isChaosTypeAllowed(webhook, decision)) {
                    deliver(webhook, payload, 0L, 1); // 0L = event not yet persisted
                }
            });
        } catch (Exception e) {
            log.error("Error in fireInjectionWebhooks: {}", e.getMessage(), e);
        }
    }

    /**
     * Retry a failed webhook delivery.
     * Called by WebhookRetryScheduler.
     */
    @Transactional
    public void retryFailedDelivery(Long webhookId) {
        try {
            WebhookConfig webhook = webhookRepository.findById(webhookId).orElse(null);
            if (webhook == null || !webhook.getEnabled()) {
                log.warn("Webhook {} not found or disabled, skipping retry", webhookId);
                return;
            }

            // Find failed deliveries ready for retry
            List<WebhookDelivery> failedDeliveries = deliveryRepository
                    .findFailedDeliveriesReadyForRetry(Instant.now())
                    .stream()
                    .filter(d -> d.getWebhookId().equals(webhookId))
                    .toList();

            for (WebhookDelivery delivery : failedDeliveries) {
                if (delivery.getRequestPayload() == null || delivery.getRequestPayload().isBlank()) {
                    log.error("Delivery {} has no payload stored, cannot retry", delivery.getId());
                    delivery.setStatus("FAILED");
                    delivery.setErrorMessage("Payload not stored, retry impossible");
                    delivery.setNextRetryAt(null); // Stop retrying
                    deliveryRepository.save(delivery);
                    continue;
                }

                // Increment attempt before retry
                delivery.setAttempt(delivery.getAttempt() + 1);

                try {
                    // Reconstruct payload from stored JSON — TypeReference avoids unchecked cast
                    Map<String, Object> payload = objectMapper.readValue(
                            delivery.getRequestPayload(),
                            new TypeReference<Map<String, Object>>() {}
                    );

                    // Attempt delivery
                    String body = objectMapper.writeValueAsString(payload);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.add("X-ChaosLab-Event", "chaos.injected");
                    headers.add("X-ChaosLab-Delivery", UUID.randomUUID().toString());
                    headers.add("X-ChaosLab-Timestamp", Instant.now().toString());
                    headers.add("X-ChaosLab-Retry-Attempt", String.valueOf(delivery.getAttempt()));

                    if (webhook.getSecret() != null) {
                        headers.add("X-ChaosLab-Signature", sign(body, webhook.getSecret()));
                    }

                    ResponseEntity<String> response = restTemplate.postForEntity(
                            webhook.getUrl(),
                            new HttpEntity<>(body, headers),
                            String.class);

                    if (response.getStatusCode().is2xxSuccessful()) {
                        delivery.setStatus("SUCCESS");
                        delivery.setHttpStatus(response.getStatusCode().value());
                        delivery.setDeliveredAt(Instant.now());
                        delivery.setNextRetryAt(null); // Clear retry
                        log.info("Webhook retry SUCCESS: '{}' → {} on attempt {}/{}",
                                webhook.getName(), webhook.getUrl(),
                                delivery.getAttempt(), delivery.getMaxAttempts());
                    } else {
                        handleFailedRetry(delivery, response.getStatusCode().value(),
                                "HTTP " + response.getStatusCode().value());
                    }

                } catch (Exception e) {
                    handleFailedRetry(delivery, null, e.getMessage());
                }

                deliveryRepository.save(delivery);
            }

        } catch (Exception e) {
            log.error("Error retrying webhook {}: {}", webhookId, e.getMessage(), e);
        }
    }

    private void deliver(WebhookConfig webhook, Map<String, Object> payload, Long chaosEventId, int attempt) {
        // Create delivery record
        WebhookDelivery delivery = WebhookDelivery.builder()
                .webhookId(webhook.getId())
                .chaosEventId(chaosEventId != null && chaosEventId > 0 ? chaosEventId : 0L)
                .status("PENDING")
                .attempt(attempt)
                .maxAttempts(3)
                .build();

        try {
            String body = objectMapper.writeValueAsString(payload);

            // ✅ CRITICAL FIX: Store payload for retry
            delivery.setRequestPayload(body);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("X-ChaosLab-Event", "chaos.injected");
            headers.add("X-ChaosLab-Delivery", UUID.randomUUID().toString());
            headers.add("X-ChaosLab-Timestamp", Instant.now().toString());

            if (webhook.getSecret() != null) {
                headers.add("X-ChaosLab-Signature", sign(body, webhook.getSecret()));
            }

            ResponseEntity<String> response = restTemplate.postForEntity(
                    webhook.getUrl(),
                    new HttpEntity<>(body, headers),
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                delivery.setStatus("SUCCESS");
                delivery.setHttpStatus(response.getStatusCode().value());
                delivery.setDeliveredAt(Instant.now());
                log.info("Webhook delivered: '{}' → {} ({})",
                        webhook.getName(), webhook.getUrl(), response.getStatusCode());
            } else {
                handleFailedDelivery(delivery, response.getStatusCode().value(),
                        "HTTP " + response.getStatusCode().value());
            }

        } catch (Exception e) {
            handleFailedDelivery(delivery, null, e.getMessage());
        } finally {
            deliveryRepository.save(delivery);
        }
    }

    private void handleFailedDelivery(WebhookDelivery delivery, Integer httpStatus, String errorMessage) {
        delivery.setStatus("FAILED");
        delivery.setHttpStatus(httpStatus);
        delivery.setErrorMessage(errorMessage);

        // Exponential backoff: 1min → 5min → 30min
        if (delivery.getAttempt() < delivery.getMaxAttempts()) {
            long delayMinutes = switch (delivery.getAttempt()) {
                case 1 -> 1;
                case 2 -> 5;
                default -> 30;
            };
            delivery.setNextRetryAt(Instant.now().plusSeconds(delayMinutes * 60));
            log.warn("Webhook delivery failed (attempt {}/{}), will retry in {} minutes",
                    delivery.getAttempt(), delivery.getMaxAttempts(), delayMinutes);
        } else {
            log.error("Webhook delivery failed after {} attempts, giving up", delivery.getAttempt());
        }
    }

    private void handleFailedRetry(WebhookDelivery delivery, Integer httpStatus, String errorMessage) {
        delivery.setStatus("FAILED");
        delivery.setHttpStatus(httpStatus);
        delivery.setErrorMessage(errorMessage);

        if (delivery.getAttempt() >= delivery.getMaxAttempts()) {
            delivery.setNextRetryAt(null); // Stop retrying
            log.error("Webhook delivery {} exhausted all {} attempts",
                    delivery.getId(), delivery.getMaxAttempts());
        } else {
            // Schedule next retry with exponential backoff
            long delayMinutes = switch (delivery.getAttempt()) {
                case 1 -> 1;
                case 2 -> 5;
                default -> 30;
            };
            delivery.setNextRetryAt(Instant.now().plusSeconds(delayMinutes * 60));
            log.warn("Webhook retry failed (attempt {}/{}), next retry in {} minutes",
                    delivery.getAttempt(), delivery.getMaxAttempts(), delayMinutes);
        }
    }

    private Map<String, Object> buildPayload(Organization org,
                                             String target,
                                             String requestId,
                                             ChaosDecision decision) {
        return Map.of(
                "event", "chaos.injected",
                "timestamp", Instant.now().toString(),
                "organization", org.getSlug(),
                "target", target,
                "requestId", requestId,
                "injected", true,
                "chaosType", decision.getChaosType() != null
                        ? decision.getChaosType().name()
                        : "NONE",
                "httpStatus", decision.getErrorCode(),
                "delayMs", decision.getDelayMs()
        );
    }

    private boolean isChaosTypeAllowed(WebhookConfig webhook, ChaosDecision decision) {
        if (webhook.getChaosTypes() == null || webhook.getChaosTypes().isBlank()) {
            return true;
        }
        String typeName = decision.getChaosType() != null
                ? decision.getChaosType().name()
                : "";
        return List.of(webhook.getChaosTypes().split(",")).contains(typeName);
    }

    private String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to sign webhook payload", e);
            return "";
        }
    }

    private WebhookResponse mapToResponse(WebhookConfig config) {
        return WebhookResponse.builder()
                .id(config.getId())
                .organizationId(config.getOrganization().getId())
                .name(config.getName())
                .url(config.getUrl())
                .hasSecret(config.getSecret() != null && !config.getSecret().isBlank())
                .enabled(config.getEnabled())
                .onInjection(config.getOnInjection())
                .onSkipped(config.getOnSkipped())
                .chaosTypes(config.getChaosTypes())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}