package com.adit.mockDemo.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for hashing API keys before DB storage or lookup.
 * We store the SHA-256 hex hash — never the raw key.
 * The raw key is returned once at creation and never persisted.
 */
@Component
@Slf4j
public class ApiKeyHasher {

    private static final String ALGORITHM = "SHA-256";

    /**
     * Hash an API key for DB storage or lookup.
     * Deterministic: same input always produces same output.
     */
    public String hash(String rawApiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(rawApiKey.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JVM spec — this cannot happen
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
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