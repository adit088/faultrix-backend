package com.adit.mockDemo.entity;

import com.adit.mockDemo.chaos.execution.ChaosType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "chaos_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChaosEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    // Nullable — rule may be deleted after event was recorded
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chaos_rule_id")
    private ChaosRuleEntity chaosRule;

    @Column(nullable = false, length = 100)
    private String target;

    @Column(nullable = false, length = 64)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChaosType chaosType;

    @Column(nullable = false)
    private Boolean injected;

    @Column
    private Integer httpStatus;

    @Column(nullable = false)
    private Integer delayMs;

    @Column(nullable = false)
    private Double failureRate;

    @Column(nullable = false)
    private Double blastRadius;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    // ✅ ADDED: Optimistic locking (defensive, even though events are append-only)
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        occurredAt = Instant.now();
        if (delayMs == null) delayMs = 0;
        if (version == null) version = 0L;
    }
}