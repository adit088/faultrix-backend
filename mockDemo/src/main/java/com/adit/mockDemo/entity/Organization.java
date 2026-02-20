package com.adit.mockDemo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    @NotBlank(message = "Organization name cannot be blank")
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    @NotBlank(message = "Slug cannot be blank")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
    private String slug;

    @Column(nullable = false, unique = true, length = 64)
    @NotBlank(message = "API key cannot be blank")
    private String apiKey;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(nullable = false, length = 50)
    @NotBlank(message = "Plan cannot be blank")
    @Builder.Default
    private String plan = "free";

    @Column(nullable = false)
    @Min(value = 1, message = "Max rules must be at least 1")
    @Builder.Default
    private Integer maxRules = 10;

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
        if (enabled == null) enabled = true;
        if (plan == null) plan = "free";
        if (maxRules == null) maxRules = 10;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}