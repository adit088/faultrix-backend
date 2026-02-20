package com.adit.mockDemo.repository;

import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.entity.WebhookConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, Long> {

    List<WebhookConfig> findByOrganization(Organization org);

    List<WebhookConfig> findByOrganizationAndEnabledTrue(Organization org);

    @Query("""
        SELECT w FROM WebhookConfig w
        WHERE w.organization = :org
          AND w.enabled = TRUE
          AND w.onInjection = TRUE
        """)
    List<WebhookConfig> findActiveForInjection(@Param("org") Organization org);

    @Query("""
        SELECT DISTINCT wd.webhookId
        FROM WebhookDelivery wd
        WHERE wd.status = 'FAILED'
          AND wd.nextRetryAt IS NOT NULL
          AND wd.nextRetryAt <= :now
          AND wd.attempt < wd.maxAttempts
        """)
    List<Long> findWebhooksReadyForRetry(@Param("now") Instant now);
}