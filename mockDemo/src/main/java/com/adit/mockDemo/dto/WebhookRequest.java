package com.adit.mockDemo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create or update a webhook configuration")
public class WebhookRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotBlank(message = "URL is required")
    @URL(message = "Must be a valid URL")
    @Size(max = 500, message = "URL must not exceed 500 characters")
    private String url;

    @Size(max = 128, message = "Secret must not exceed 128 characters")
    @Schema(description = "Optional HMAC-SHA256 signing secret")
    private String secret;

    @Builder.Default
    private Boolean enabled = true;

    @Builder.Default
    @Schema(description = "Fire webhook when chaos is injected")
    private Boolean onInjection = true;

    @Builder.Default
    @Schema(description = "Fire webhook when chaos is skipped")
    private Boolean onSkipped = false;

    @Size(max = 200)
    @Schema(description = "CSV of ChaosType names to filter. Null = all types. E.g. ERROR_5XX,TIMEOUT")
    private String chaosTypes;
}