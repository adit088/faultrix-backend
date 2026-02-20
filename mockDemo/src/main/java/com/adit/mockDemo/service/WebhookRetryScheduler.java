package com.adit.mockDemo.service;

import com.adit.mockDemo.repository.WebhookConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Processes failed webhook deliveries with exponential backoff.
 * Runs every minute to check for webhooks ready for retry.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookRetryScheduler {

    private final WebhookService webhookService;
    private final WebhookConfigRepository webhookConfigRepository;

    @Scheduled(fixedRate = 60_000) // Every 1 minute
    @Transactional
    public void retryFailedWebhooks() {
        try {
            Instant now = Instant.now();

            // Find all webhooks that have failed deliveries due for retry
            List<Long> webhooksReadyForRetry = webhookConfigRepository.findWebhooksReadyForRetry(now);

            if (webhooksReadyForRetry.isEmpty()) {
                return;
            }

            log.info("Found {} webhooks ready for retry", webhooksReadyForRetry.size());

            for (Long webhookId : webhooksReadyForRetry) {
                try {
                    webhookService.retryFailedDelivery(webhookId);
                } catch (Exception e) {
                    log.error("Error retrying webhook {}: {}", webhookId, e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error in webhook retry scheduler: {}", e.getMessage(), e);
        }
    }
}