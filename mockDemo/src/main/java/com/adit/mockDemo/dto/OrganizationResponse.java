package com.adit.mockDemo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationResponse {
    private Long id;
    private String name;
    private String slug;
    private String apiKey;   // ← added — raw key returned once at creation only
    private String plan;
    private Integer maxRules;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}