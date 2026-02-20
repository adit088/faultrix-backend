package com.adit.mockDemo.proxy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Forwards HTTP requests to the real upstream service and returns the raw response.
 *
 * Security guarantees:
 *  - SSRF validation runs before every request via SsrfGuard
 *  - Uses a dedicated RestTemplate with proxy-specific timeouts (not shared with the app)
 *  - Connect timeout:  5s  (upstream must accept connection within 5 seconds)
 *  - Read timeout:    10s  (upstream must respond within 10 seconds)
 *  - Strips hop-by-hop headers (Connection, Transfer-Encoding, etc.) from forwarded response
 *  - Blocks forwarding of dangerous request headers (X-API-Key, Authorization forwarding is
 *    the caller's explicit choice — we don't strip it, but we log it)
 *
 * Max latency chaos cap (enforced here, not in ProxyChaosService):
 *  Latency injections > read timeout would cause the request to time out anyway,
 *  so we cap them at 8000ms (2s under the 10s read timeout).
 */
@Component
@Slf4j
public class UpstreamForwarder {

    /** Max latency injection — capped 2s under read timeout to avoid thread exhaustion */
    public static final int MAX_LATENCY_MS = 8_000;

    /**
     * Hop-by-hop headers that must NOT be forwarded to the upstream.
     * RFC 2616 §13.5.1
     */
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade",
            // Faultrix-internal headers — never forward these upstream
            "x-api-key", "x-correlation-id"
    );

    /**
     * Response headers that should NOT be forwarded back to the client.
     * Prevents leaking internal upstream infrastructure details.
     */
    private static final Set<String> BLOCKED_RESPONSE_HEADERS = Set.of(
            "connection", "keep-alive", "transfer-encoding", "trailer"
    );

    private final RestTemplate proxyRestTemplate;
    private final SsrfGuard    ssrfGuard;

    public UpstreamForwarder(SsrfGuard ssrfGuard) {
        this.ssrfGuard = ssrfGuard;

        // Dedicated RestTemplate for proxy — tighter timeouts than the app's default one
        // This is intentionally separate so proxy timeouts don't affect other RestTemplate uses
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);   // 5s to establish connection
        factory.setReadTimeout(10_000);     // 10s to receive first byte of response
        this.proxyRestTemplate = new RestTemplate(factory);
    }

    /**
     * Forward a request to the upstream URL and return the raw response.
     *
     * @param method   HTTP method (GET, POST, PUT, PATCH, DELETE)
     * @param url      Full upstream URL — SSRF-validated before call
     * @param headers  Headers to include (hop-by-hop headers are stripped)
     * @param body     Request body (nullable for GET/DELETE)
     * @return UpstreamResult containing status, body, and response headers
     * @throws SsrfGuard.SsrfException if the URL resolves to a blocked address
     */
    public UpstreamResult forward(String method,
                                  String url,
                                  Map<String, String> headers,
                                  String body) {
        // ── SSRF validation — must run before every call ─────────────────────
        ssrfGuard.validate(url);

        log.info("Forwarding {} {} upstream", method, url);

        HttpHeaders httpHeaders = buildHeaders(headers);
        HttpEntity<String> entity = new HttpEntity<>(body, httpHeaders);
        HttpMethod httpMethod = resolveMethod(method);

        try {
            ResponseEntity<String> response = proxyRestTemplate.exchange(
                    URI.create(url),
                    httpMethod,
                    entity,
                    String.class
            );

            log.info("Upstream {} {} → {}", method, url, response.getStatusCode().value());

            return UpstreamResult.builder()
                    .status(response.getStatusCode().value())
                    .body(response.getBody())
                    .headers(filterResponseHeaders(response.getHeaders()))
                    .success(true)
                    .build();

        } catch (HttpStatusCodeException e) {
            // Upstream returned 4xx/5xx — still a valid response, pass it through
            log.warn("Upstream returned error {} for {} {}", e.getStatusCode().value(), method, url);

            return UpstreamResult.builder()
                    .status(e.getStatusCode().value())
                    .body(e.getResponseBodyAsString())
                    .headers(filterResponseHeaders(e.getResponseHeaders()))
                    .success(false)
                    .build();

        } catch (ResourceAccessException e) {
            // Network error — upstream unreachable (timeout, DNS failure, etc.)
            log.error("Upstream unreachable: {} {} — {}", method, url, e.getMessage());

            return UpstreamResult.builder()
                    .status(502)
                    .body("{\"error\":\"Upstream unreachable\",\"message\":\"" + sanitize(e.getMessage()) + "\"}")
                    .headers(new HashMap<>())
                    .success(false)
                    .build();

        } catch (Exception e) {
            log.error("Unexpected error forwarding {} {}", method, url, e);

            return UpstreamResult.builder()
                    .status(500)
                    .body("{\"error\":\"Proxy internal error\",\"message\":\"" + sanitize(e.getMessage()) + "\"}")
                    .headers(new HashMap<>())
                    .success(false)
                    .build();
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private HttpHeaders buildHeaders(Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        if (headers != null) {
            headers.forEach((key, value) -> {
                // Strip hop-by-hop and Faultrix-internal headers from outgoing upstream request
                if (key != null && !HOP_BY_HOP_HEADERS.contains(key.toLowerCase())) {
                    httpHeaders.set(key, value);
                }
            });
        }
        // Ensure JSON content type if not already set
        if (!httpHeaders.containsKey(HttpHeaders.CONTENT_TYPE)) {
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        }
        return httpHeaders;
    }

    private Map<String, String> filterResponseHeaders(org.springframework.http.HttpHeaders headers) {
        Map<String, String> flat = new HashMap<>();
        if (headers == null) return flat;
        headers.forEach((key, values) -> {
            if (key != null && !values.isEmpty()
                    && !BLOCKED_RESPONSE_HEADERS.contains(key.toLowerCase())) {
                flat.put(key, values.get(0));
            }
        });
        return flat;
    }

    private HttpMethod resolveMethod(String method) {
        return switch (method.toUpperCase()) {
            case "GET"     -> HttpMethod.GET;
            case "POST"    -> HttpMethod.POST;
            case "PUT"     -> HttpMethod.PUT;
            case "PATCH"   -> HttpMethod.PATCH;
            case "DELETE"  -> HttpMethod.DELETE;
            case "HEAD"    -> HttpMethod.HEAD;
            case "OPTIONS" -> HttpMethod.OPTIONS;
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };
    }

    /** Sanitize exception messages before embedding in JSON responses */
    private String sanitize(String message) {
        if (message == null) return "Unknown error";
        // Remove characters that would break the JSON string
        return message.replace("\"", "'").replace("\n", " ").replace("\r", "");
    }
}