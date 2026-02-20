package com.adit.mockDemo.entity;

import com.adit.mockDemo.chaos.execution.TargetingMode;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "chaos_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChaosRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 100)
    @NotBlank(message = "Target cannot be blank")
    private String target;

    // ── Advanced targeting ───────────────────────────────────────────────────

    @Column(length = 200)
    private String targetPattern;   // regex or prefix pattern

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private TargetingMode targetingMode = TargetingMode.EXACT;

    // ── Chaos config ─────────────────────────────────────────────────────────

    @Column(nullable = false)
    @DecimalMin(value = "0.0", message = "Failure rate must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "Failure rate must be between 0.0 and 1.0")
    private Double failureRate;

    @Column(nullable = false)
    @Min(value = 0,     message = "Max delay cannot be negative")
    @Max(value = 30000, message = "Max delay cannot exceed 30000ms")
    private Long maxDelayMs;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(length = 500)
    private String description;

    @Column(length = 200)
    private String tags;

    @Column
    private Long seed;

    @Column(nullable = false)
    @DecimalMin(value = "0.0", message = "Blast radius must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "Blast radius must be between 0.0 and 1.0")
    @Builder.Default
    private Double blastRadius = 1.0;

    // ── Audit ────────────────────────────────────────────────────────────────

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(updatable = false, length = 100)
    private String createdBy;

    @Column(length = 100)
    private String updatedBy;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (enabled      == null) enabled      = false;
        if (failureRate  == null) failureRate   = 0.0;
        if (maxDelayMs   == null) maxDelayMs    = 0L;
        if (blastRadius  == null) blastRadius   = 1.0;
        if (targetingMode == null) targetingMode = TargetingMode.EXACT;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}