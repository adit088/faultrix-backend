package com.adit.mockDemo.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHasherTest {

    private ApiKeyHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new ApiKeyHasher();
    }

    @Test
    void hash_returnsNonNullNonEmpty() {
        String result = hasher.hash("ck_test_abc123");
        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    void hash_returnsSha256HexLength64() {
        String result = hasher.hash("any-key");
        assertThat(result).hasSize(64);
    }

    @Test
    void hash_isDeterministic() {
        String key = "ck_test_default_1234567890abcdef";
        assertThat(hasher.hash(key)).isEqualTo(hasher.hash(key));
    }

    @Test
    void hash_differentInputsProduceDifferentHashes() {
        assertThat(hasher.hash("key-one")).isNotEqualTo(hasher.hash("key-two"));
    }

    @Test
    void hash_isLowerCaseHex() {
        String result = hasher.hash("test-key");
        assertThat(result).matches("[0-9a-f]{64}");
    }

    @Test
    void hash_knownValue() {
        // Pre-computed: SHA-256("ck_test_default_1234567890abcdef")
        // Verified with: echo -n "ck_test_default_1234567890abcdef" | sha256sum
        String result = hasher.hash("ck_test_default_1234567890abcdef");
        assertThat(result).isEqualTo("ac5fb9ca6ee99375f2d20d279dd122fc5bd92fa8f87677797482496339c100d3");
    }
}