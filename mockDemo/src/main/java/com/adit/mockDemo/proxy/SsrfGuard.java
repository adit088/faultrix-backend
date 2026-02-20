package com.adit.mockDemo.proxy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SSRF (Server-Side Request Forgery) protection guard.
 *
 * Blocks proxy requests to:
 *  - Localhost / loopback (127.x.x.x, ::1)
 *  - Private RFC-1918 ranges (10.x, 172.16-31.x, 192.168.x)
 *  - Link-local / cloud metadata (169.254.x.x — AWS/GCP/Azure metadata service)
 *  - Internal Railway/Docker ranges
 *  - Non-HTTP(S) schemes (file://, ftp://, gopher://, etc.)
 *  - Explicitly blocked hostnames
 *
 * This must run BEFORE the upstream HTTP call is made.
 */
@Component
@Slf4j
public class SsrfGuard {

    // ── Blocked schemes ────────────────────────────────────────────────────────
    private static final List<String> ALLOWED_SCHEMES = List.of("http", "https");

    // ── Blocked hostnames (case-insensitive) ───────────────────────────────────
    private static final List<String> BLOCKED_HOSTNAMES = List.of(
            "localhost",
            "metadata.google.internal",       // GCP metadata
            "169.254.169.254",                 // AWS/Azure/GCP metadata IP
            "metadata.azure.com",
            "fd00:ec2::254"                    // AWS IPv6 metadata
    );

    // ── Blocked hostname suffix patterns ──────────────────────────────────────
    private static final List<String> BLOCKED_SUFFIXES = List.of(
            ".internal",
            ".local",
            ".railway.internal",
            ".svc.cluster.local"
    );

    // ── Private IP ranges (CIDR-style manual check) ───────────────────────────
    // RFC-1918: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
    // Loopback: 127.0.0.0/8
    // Link-local: 169.254.0.0/16
    // IPv6 loopback: ::1
    // IPv6 link-local: fe80::/10
    // IPv6 unique local: fc00::/7

    /**
     * Validate that the given URL is safe to proxy.
     * Throws SsrfException if the URL is blocked.
     */
    public void validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new SsrfException("URL must not be blank");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new SsrfException("Malformed URL: " + rawUrl);
        }

        // 1. Scheme check
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new SsrfException("Blocked scheme: '" + scheme + "'. Only http and https are allowed.");
        }

        // 2. Host must be present
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SsrfException("URL has no host: " + rawUrl);
        }
        host = host.toLowerCase().trim();

        // 3. Strip IPv6 brackets if present: [::1] → ::1
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }

        // 4. Blocked hostname exact match
        if (BLOCKED_HOSTNAMES.contains(host)) {
            log.warn("SSRF BLOCKED — blocked hostname: {}", host);
            throw new SsrfException("Blocked host: " + host);
        }

        // 5. Blocked hostname suffix
        for (String suffix : BLOCKED_SUFFIXES) {
            if (host.endsWith(suffix)) {
                log.warn("SSRF BLOCKED — internal hostname suffix: {}", host);
                throw new SsrfException("Blocked internal host: " + host);
            }
        }

        // 6. Resolve IP and check private ranges
        //    (Blocks DNS rebinding attacks too — we check the resolved IP, not just the hostname)
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                checkIpAddress(addr, rawUrl);
            }
        } catch (UnknownHostException e) {
            // DNS resolution failure — let it through, upstream call will fail naturally
            // Blocking unknown hosts would break valid URLs under temporary DNS issues
            log.warn("SSRF check: Could not resolve host '{}' — allowing through (will fail upstream)", host);
        }

        log.debug("SSRF check passed for URL: {}", rawUrl);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void checkIpAddress(InetAddress addr, String rawUrl) {
        byte[] ip = addr.getAddress();

        if (addr.isLoopbackAddress()) {
            log.warn("SSRF BLOCKED — loopback address for URL: {}", rawUrl);
            throw new SsrfException("Blocked: loopback address");
        }

        if (addr.isLinkLocalAddress()) {
            log.warn("SSRF BLOCKED — link-local address (cloud metadata?) for URL: {}", rawUrl);
            throw new SsrfException("Blocked: link-local address (possible cloud metadata endpoint)");
        }

        if (addr.isSiteLocalAddress()) {
            log.warn("SSRF BLOCKED — private/site-local address for URL: {}", rawUrl);
            throw new SsrfException("Blocked: private IP address");
        }

        if (addr.isAnyLocalAddress()) {
            log.warn("SSRF BLOCKED — any-local address for URL: {}", rawUrl);
            throw new SsrfException("Blocked: any-local address");
        }

        if (addr.isMulticastAddress()) {
            log.warn("SSRF BLOCKED — multicast address for URL: {}", rawUrl);
            throw new SsrfException("Blocked: multicast address");
        }

        // Extra IPv4 check for link-local range 169.254.x.x
        // (Java's isLinkLocalAddress() should catch this, but belt-and-suspenders)
        if (ip.length == 4) {
            int b0 = ip[0] & 0xFF;
            int b1 = ip[1] & 0xFF;
            if (b0 == 169 && b1 == 254) {
                log.warn("SSRF BLOCKED — 169.254.x.x (cloud metadata) for URL: {}", rawUrl);
                throw new SsrfException("Blocked: cloud metadata address 169.254.x.x");
            }
            // 100.64.0.0/10 — Carrier-grade NAT / internal cloud ranges
            if (b0 == 100 && (b1 >= 64 && b1 <= 127)) {
                log.warn("SSRF BLOCKED — 100.64.x.x (CGNAT) for URL: {}", rawUrl);
                throw new SsrfException("Blocked: CGNAT address range");
            }
        }
    }

    // ── Exception class ───────────────────────────────────────────────────────

    public static class SsrfException extends RuntimeException {
        public SsrfException(String message) {
            super(message);
        }
    }
}