package com.adit.mockDemo.chaos.execution;

import com.adit.mockDemo.entity.ChaosRuleEntity;
import com.adit.mockDemo.entity.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TargetMatcherTest {

    private TargetMatcher matcher;
    private Organization testOrg;

    @BeforeEach
    void setUp() {
        matcher = new TargetMatcher();
        testOrg = Organization.builder()
                .id(1L)
                .name("Test Org")
                .build();
    }

    @Test
    void matches_exactMode_exactMatch() {
        ChaosRuleEntity rule = ChaosRuleEntity.builder()
                .organization(testOrg)
                .target("/api/v1/users")
                .targetingMode(TargetingMode.EXACT)
                .build();

        boolean result = matcher.matches(rule, "/api/v1/users");

        assertThat(result).isTrue();
    }

    @Test
    void matches_exactMode_noMatch() {
        ChaosRuleEntity rule = ChaosRuleEntity.builder()
                .organization(testOrg)
                .target("/api/v1/users")
                .targetingMode(TargetingMode.EXACT)
                .build();

        boolean result = matcher.matches(rule, "/api/v1/orders");

        assertThat(result).isFalse();
    }

    @Test
    void matches_prefixMode_matchesPrefix() {
        ChaosRuleEntity rule = ChaosRuleEntity.builder()
                .organization(testOrg)
                .target("/api/v1/users")
                .targetPattern("/api/v1/users")
                .targetingMode(TargetingMode.PREFIX)
                .build();

        boolean result = matcher.matches(rule, "/api/v1/users/123");

        assertThat(result).isTrue();
    }

    @Test
    void matches_prefixMode_noMatch() {
        ChaosRuleEntity rule = ChaosRuleEntity.builder()
                .organization(testOrg)
                .target("/api/v1/users")
                .targetPattern("/api/v1/users")
                .targetingMode(TargetingMode.PREFIX)
                .build();

        boolean result = matcher.matches(rule, "/api/v1/orders/456");

        assertThat(result).isFalse();
    }

    @Test
    void matches_regexMode_simplePattern() {
        ChaosRuleEntity rule = ChaosRuleEntity.builder()
                .organization(testOrg)
                .target("/api/v1/users")
                .targetPattern("/api/v1/users/.*")
                .targetingMode(TargetingMode.REGEX)
                .build();

        boolean result = matcher.matches(rule, "/api/v1/users/123");

        assertThat(result).isTrue();
    }

    @Test
    void matches_regexMode_complexPattern() {
        ChaosRuleEntity rule = ChaosRuleEntity.builder()
                .organization(testOrg)
                .target("/api/v1/users")
                .targetPattern("/api/v1/users/\\d+")
                .targetingMode(TargetingMode.REGEX)
                .build();

        boolean result = matcher.matches(rule, "/api/v1/users/123");

        assertThat(result).isTrue();
    }

    @Test
    void matches_regexMode_noMatch() {
        ChaosRuleEntity rule = ChaosRuleEntity.builder()
                .organization(testOrg)
                .target("/api/v1/users")
                .targetPattern("/api/v1/users/\\d+")
                .targetingMode(TargetingMode.REGEX)
                .build();

        boolean result = matcher.matches(rule, "/api/v1/users/abc");

        assertThat(result).isFalse();
    }

    @Test
    void isValidRegex_validPattern_returnsTrue() {
        boolean result = matcher.isValidRegex("/api/v1/users/\\d+");

        assertThat(result).isTrue();
    }

    @Test
    void isValidRegex_invalidPattern_returnsFalse() {
        boolean result = matcher.isValidRegex("/api/v1/users/[");

        assertThat(result).isFalse();
    }

    @Test
    void evictPattern_removesFromCache() {
        String pattern = "/api/v1/users/.*";

        ChaosRuleEntity rule = ChaosRuleEntity.builder()
                .organization(testOrg)
                .target("/api/v1/users")
                .targetPattern(pattern)
                .targetingMode(TargetingMode.REGEX)
                .build();

        matcher.matches(rule, "/api/v1/users/123");
        matcher.evictPattern(pattern);

        // Should still work after eviction
        boolean result = matcher.matches(rule, "/api/v1/users/456");
        assertThat(result).isTrue();
    }
}