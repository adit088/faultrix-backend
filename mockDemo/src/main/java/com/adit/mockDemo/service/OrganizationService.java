package com.adit.mockDemo.service;

import com.adit.mockDemo.dto.OrganizationResponse;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.exception.ResourceNotFoundException;
import com.adit.mockDemo.exception.ValidationException;
import com.adit.mockDemo.repository.OrganizationRepository;
import com.adit.mockDemo.security.ApiKeyHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final ApiKeyHasher           apiKeyHasher;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional(readOnly = true)
    public Organization getBySlug(String slug) {
        return organizationRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", slug));
    }

    /**
     * Creates a new organization.
     *
     * IMPORTANT: The raw API key is returned in the response and NEVER stored.
     * The DB stores only the SHA-256 hash. If the key is lost, a new one must be generated.
     *
     * @return OrganizationResponse containing the plaintext key — show it once, then discard
     */
    public OrganizationResponse createOrganization(String name, String plan) {
        log.info("Creating organization: {}, plan: {}", name, plan);

        String slug = generateSlug(name);

        if (organizationRepository.existsBySlug(slug)) {
            throw new ValidationException("Organization with slug '" + slug + "' already exists");
        }

        // Generate raw key — returned to caller, never stored
        // Mode reflects the plan tier so keys are visually distinguishable (ck_live_XXX vs ck_test_XXX)
        String keyMode   = "free".equalsIgnoreCase(plan) ? "test" : "live";
        String rawApiKey = generateRawApiKey(keyMode);

        // Store only the hash
        String hashedApiKey = apiKeyHasher.hash(rawApiKey);

        Integer maxRules = getMaxRulesForPlan(plan);

        Organization org = Organization.builder()
                .name(name)
                .slug(slug)
                .apiKey(hashedApiKey)   // ← hash stored, not raw key
                .plan(plan)
                .maxRules(maxRules)
                .enabled(true)
                .build();

        Organization saved = organizationRepository.save(org);
        log.info("Created organization: {} (id: {})", saved.getSlug(), saved.getId());

        // Return response with plaintext key included once — caller must store it
        return OrganizationResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .slug(saved.getSlug())
                .apiKey(rawApiKey)      // ← raw key returned once to caller
                .plan(saved.getPlan())
                .maxRules(saved.getMaxRules())
                .enabled(saved.getEnabled())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    private String generateRawApiKey(String mode) {
        byte[] randomBytes = new byte[24];
        SECURE_RANDOM.nextBytes(randomBytes);
        String random = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return String.format("ck_%s_%s", mode, random);
    }

    private String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private Integer getMaxRulesForPlan(String plan) {
        return switch (plan.toLowerCase()) {
            case "free"       -> 10;
            case "pro"        -> 100;
            case "enterprise" -> 999;
            default           -> 10;
        };
    }
}