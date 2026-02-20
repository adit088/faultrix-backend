package com.adit.mockDemo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test - verifies Spring Boot context loads successfully.
 * If this fails, the entire app is broken.
 */
@SpringBootTest
@ActiveProfiles("test")
class MockDemoApplicationTests {

    @Test
    void contextLoads() {
        // If Spring context fails to load, this test will fail
        // This is the most basic sanity check
    }

    @Test
    void applicationStarts() {
        // Verifies @SpringBootApplication annotation is correct
        // and all auto-configurations work
    }
}