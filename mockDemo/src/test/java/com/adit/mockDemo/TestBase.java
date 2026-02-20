package com.adit.mockDemo;

import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.repository.OrganizationRepository;
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

    protected Organization testOrg;
    protected String testApiKey = "test_api_key_12345";

    @BeforeEach
    void setUpBase() {
        testOrg = Organization.builder()
                .name("Test Organization")
                .slug("test-org")
                .apiKey(testApiKey)
                .enabled(true)
                .plan("enterprise")
                .maxRules(100)
                .build();
        testOrg = organizationRepository.save(testOrg);
    }
}