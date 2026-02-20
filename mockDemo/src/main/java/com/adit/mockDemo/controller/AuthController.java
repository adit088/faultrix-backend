package com.adit.mockDemo.controller;

import com.adit.mockDemo.dto.OrganizationResponse;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.exception.ValidationException;
import com.adit.mockDemo.repository.OrganizationRepository;
import com.adit.mockDemo.security.ApiKeyHasher;
import com.adit.mockDemo.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public auth endpoints — no API key required (excluded from ApiKeyAuthFilter via PUBLIC_PREFIXES).
 *
 * POST /api/v1/auth/register  — create org, returns raw API key once
 * POST /api/v1/auth/login     — lookup org by API key, returns org info (key re-validation)
 */
@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration and login — no API key required")
public class AuthController {

    private final OrganizationService    organizationService;
    private final OrganizationRepository organizationRepository;
    private final ApiKeyHasher           apiKeyHasher;

    // ── Register ──────────────────────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(
            summary = "Register a new organization",
            description = """
                    Creates a new organization and returns a raw API key.
                    The key is shown ONCE — store it immediately, it cannot be retrieved again.
                    The DB stores only the SHA-256 hash.
                    """
    )
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        log.info("REGISTER — org: {}, email: {}", req.getOrgName(), req.getEmail());

        // Delegate to OrganizationService which handles slug gen, key gen, hashing
        OrganizationResponse org = organizationService.createOrganization(
                req.getOrgName().trim(),
                "free"   // all new orgs start on free plan
        );

        RegisterResponse res = new RegisterResponse();
        res.setOrgId(org.getId());
        res.setOrgName(org.getName());
        res.setSlug(org.getSlug());
        res.setApiKey(org.getApiKey());   // raw key — returned once only
        res.setPlan(org.getPlan());
        res.setMaxRules(org.getMaxRules());
        res.setMessage("Welcome to Faultrix! Store your API key — it won't be shown again.");

        log.info("REGISTERED — org: {} (id: {})", org.getSlug(), org.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(
            summary = "Validate API key and return org info",
            description = """
                    Validates the provided API key and returns organization details.
                    Used by the frontend to verify a stored key is still valid.
                    Does NOT return the raw key — only org metadata.
                    """
    )
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        log.info("LOGIN attempt");

        String hashed = apiKeyHasher.hash(req.getApiKey().trim());
        Organization org = organizationRepository.findByApiKey(hashed)
                .orElseThrow(() -> new ValidationException("Invalid API key"));

        if (!org.getEnabled()) {
            throw new ValidationException("Organization is disabled. Contact support.");
        }

        LoginResponse res = new LoginResponse();
        res.setOrgId(org.getId());
        res.setOrgName(org.getName());
        res.setSlug(org.getSlug());
        res.setPlan(org.getPlan());
        res.setMaxRules(org.getMaxRules());
        res.setValid(true);

        log.info("LOGIN success — org: {}", org.getSlug());
        return ResponseEntity.ok(res);
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "Organization name is required")
        @Size(min = 2, max = 100, message = "Name must be 2–100 characters")
        private String orgName;

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email")
        private String email;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "API key is required")
        private String apiKey;
    }

    @Data
    public static class RegisterResponse {
        private Long    orgId;
        private String  orgName;
        private String  slug;
        private String  apiKey;     // raw key — only time it's visible
        private String  plan;
        private Integer maxRules;
        private String  message;
    }

    @Data
    public static class LoginResponse {
        private Long    orgId;
        private String  orgName;
        private String  slug;
        private String  plan;
        private Integer maxRules;
        private boolean valid;
    }
}