package com.adit.mockDemo.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ApiKeyHasher — HMAC-SHA256 hashing")
class ApiKeyHasherTest {

    private static final String TEST_SECRET = "test-secret-fixed-for-deterministic-hmac";

    // Pre-computed: HMAC-SHA256("test-api-key-abc123", TEST_SECRET)
    // Verified with: python3 -c "import hmac,hashlib; print(hmac.new(b'test-secret-fixed-for-deterministic-hmac', b'test-api-key-abc123', hashlib.sha256).hexdigest())"
    private static final String EXPECTED_HASH = "eeede77f7a22d157e23faa997a7e0cd4ac5171997ac8e9873c39e86c9496ef98";

    private ApiKeyHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new ApiKeyHasher(TEST_SECRET);
    }

    @Test
    @DisplayName("hash() returns correct HMAC-SHA256 hex for known input")
    void hash_knownValue() {
        String result = hasher.hash("test-api-key-abc123");
        assertThat(result).isEqualTo(EXPECTED_HASH);
    }

    @Test
    @DisplayName("hash() is deterministic — same input always returns same hash")
    void hash_isDeterministic() {
        String h1 = hasher.hash("my-api-key");
        String h2 = hasher.hash("my-api-key");
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("hash() returns different values for different inputs")
    void hash_differentInputsDifferentOutput() {
        String h1 = hasher.hash("key-one");
        String h2 = hasher.hash("key-two");
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("hash() output changes when secret changes — proves it is HMAC, not plain SHA-256")
    void hash_differentSecretsProduceDifferentHashes() {
        ApiKeyHasher hasher2 = new ApiKeyHasher("a-completely-different-secret");
        String h1 = hasher.hash("same-key");
        String h2 = hasher2.hash("same-key");
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("hash() output is 64-char lowercase hex (256-bit = 32 bytes = 64 hex chars)")
    void hash_outputIs64CharHex() {
        String result = hasher.hash("any-key");
        assertThat(result).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("Constructor rejects blank secret — app fails to start rather than silently using no HMAC key")
    void constructor_rejectsBlankSecret() {
        assertThatThrownBy(() -> new ApiKeyHasher(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("security.api-key-secret must be set");

        assertThatThrownBy(() -> new ApiKeyHasher("   "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Constructor rejects null secret")
    void constructor_rejectsNullSecret() {
        assertThatThrownBy(() -> new ApiKeyHasher(null))
                .isInstanceOf(IllegalStateException.class);
    }
}