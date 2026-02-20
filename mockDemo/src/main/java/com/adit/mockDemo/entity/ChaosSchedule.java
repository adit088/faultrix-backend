package com.adit.mockDemo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "chaos_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChaosSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chaos_rule_id", nullable = false)
    private ChaosRuleEntity chaosRule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    // CSV of day numbers: 1=Mon, 7=Sun. "1,2,3,4,5" = weekdays
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String daysOfWeek = "1,2,3,4,5,6,7";

    // HH:mm UTC
    @Column(nullable = false, length = 5)
    @Builder.Default
    private String startTime = "00:00";

    @Column(nullable = false, length = 5)
    @Builder.Default
    private String endTime = "23:59";

    // Optional absolute window
    @Column
    private Instant activeFrom;

    @Column
    private Instant activeUntil;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}