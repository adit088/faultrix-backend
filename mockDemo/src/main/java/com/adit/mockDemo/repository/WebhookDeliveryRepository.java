package com.adit.mockDemo.repository;

import com.adit.mockDemo.entity.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    List<WebhookDelivery> findByWebhookIdOrderByCreatedAtDesc(Long webhookId);

    @Query("""
        SELECT wd FROM WebhookDelivery wd
        WHERE wd.status = 'FAILED'
          AND wd.nextRetryAt IS NOT NULL
          AND wd.nextRetryAt <= :now
          AND wd.attempt < wd.maxAttempts
        ORDER BY wd.nextRetryAt ASC
        """)
    List<WebhookDelivery> findFailedDeliveriesReadyForRetry(@Param("now") Instant now);

    Optional<WebhookDelivery> findTopByWebhookIdAndChaosEventIdOrderByAttemptDesc(Long webhookId, Long chaosEventId);
}