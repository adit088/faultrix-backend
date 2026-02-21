package com.adit.mockDemo;

import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.repository.OrganizationRepository;
import com.adit.mockDemo.security.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class TestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected OrganizationRepository organizationRepository;

    // BUG FIX: ApiKeyHasher is now injected so we can hash the test key before DB storage.
    // Previously this was missing: the raw key was stored, so ApiKeyAuthFilter (which hashes
    // the incoming X-API-Key header with HMAC-SHA256 before lookup) could never find the org.
    // Every integration test that extends TestBase was therefore unauthenticated.
    @Autowired
    private ApiKeyHasher apiKeyHasher;

    protected Organization testOrg;

    // This is the raw key you send in X-API-Key header in tests.
    // The DB stores its HMAC-SHA256 hash — NOT this value.
    protected final String testApiKey = "test_api_key_12345";

    @BeforeEach
    void setUpBase() {
        // Hash the key before storage — mirrors what OrganizationService.createOrganization() does in prod.
        String hashedKey = apiKeyHasher.hash(testApiKey);

        testOrg = Organization.builder()
                .name("Test Organization")
                .slug("test-org-" + System.nanoTime())  // unique slug per test run to avoid constraint violations
                .apiKey(hashedKey)                       // FIXED: store hash, not raw key
                .enabled(true)
                .plan("enterprise")
                .maxRules(100)
                .build();
        testOrg = organizationRepository.save(testOrg);
    }
}