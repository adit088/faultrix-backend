package com.adit.mockDemo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "webhook_deliveries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "webhook_id", nullable = false)
    private Long webhookId;

    @Column(name = "chaos_event_id", nullable = false)
    private Long chaosEventId;

    @Column(nullable = false, length = 20)
    private String status; // PENDING, SUCCESS, FAILED

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(nullable = false)
    private Integer attempt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // V9 migration added this column — entity was missing the field, breaking optimistic locking silently
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (attempt == null) {
            attempt = 1;
        }
        if (maxAttempts == null) {
            maxAttempts = 3;
        }
        if (version == null) {
            version = 0L;
        }
    }
}