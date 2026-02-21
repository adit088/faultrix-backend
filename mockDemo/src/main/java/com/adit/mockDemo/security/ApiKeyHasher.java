package com.adit.mockDemo.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Hashes API keys for DB storage and lookup using HMAC-SHA256.
 *
 * FIX SEC-3: replaced plain SHA-256 (no salt) with HMAC-SHA256 keyed on a server-side secret.
 * Plain SHA-256 is vulnerable to rainbow-table attacks if the DB is compromised.
 * HMAC-SHA256 with a server secret means pre-computed tables are useless — an attacker needs
 * both the DB dump AND the secret to brute-force a key.
 *
 * Required env var / config:
 *   security.api-key-secret=${API_KEY_SECRET}   (no default — app fails to start if unset)
 *
 * The raw key is returned once at org creation and never persisted.
 * DB stores only the HMAC hex. If the secret rotates, all existing keys become invalid — planned.
 */
@Component
@Slf4j
public class ApiKeyHasher {

    private final byte[] secretBytes;

    public ApiKeyHasher(@Value("${security.api-key-secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "security.api-key-secret must be set — add API_KEY_SECRET env var");
        }
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Deterministic HMAC-SHA256 hash of a raw API key.
     * Same input + same server secret → same output, enabling DB lookup.
     */
    public String hash(String rawApiKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] hmac = mac.doFinal(rawApiKey.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmac);
        } catch (Exception e) {
            // HmacSHA256 is guaranteed by the JVM spec — this cannot happen in practice
            throw new IllegalStateException("HmacSHA256 not available on this JVM", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}