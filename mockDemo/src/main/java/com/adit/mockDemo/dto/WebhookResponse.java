package com.adit.mockDemo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Webhook configuration response")
public class WebhookResponse {

    private Long id;
    private Long organizationId;
    private String name;
    private String url;
    private Boolean hasSecret;       // never expose the secret itself
    private Boolean enabled;
    private Boolean onInjection;
    private Boolean onSkipped;
    private String chaosTypes;
    private Instant createdAt;
    private Instant updatedAt;
}