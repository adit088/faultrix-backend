package com.adit.mockDemo.proxy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Forwards HTTP requests to the real upstream service and returns the raw response.
 *
 * Isolated here so ProxyChaosService stays clean — it only handles chaos logic.
 * All actual HTTP I/O lives in this class.
 */
@Component
@Slf4j
public class UpstreamForwarder {

    private final RestTemplate restTemplate;

    public UpstreamForwarder(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Forward a request to the upstream URL and return the raw response.
     *
     * @param method   HTTP method (GET, POST, PUT, PATCH, DELETE)
     * @param url      Full upstream URL
     * @param headers  Headers to include (nullable)
     * @param body     Request body (nullable for GET/DELETE)
     * @return UpstreamResult containing status, body, and response headers
     */
    public UpstreamResult forward(String method,
                                  String url,
                                  Map<String, String> headers,
                                  String body) {
        log.info("Forwarding {} {} upstream", method, url);

        HttpHeaders httpHeaders = buildHeaders(headers);
        HttpEntity<String> entity = new HttpEntity<>(body, httpHeaders);
        HttpMethod httpMethod = resolveMethod(method);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(url),
                    httpMethod,
                    entity,
                    String.class
            );

            log.info("Upstream {} {} → {}", method, url, response.getStatusCode().value());

            return UpstreamResult.builder()
                    .status(response.getStatusCode().value())
                    .body(response.getBody())
                    .headers(flattenHeaders(response.getHeaders()))
                    .success(true)
                    .build();

        } catch (HttpStatusCodeException e) {
            // Upstream returned 4xx/5xx — still a valid response, pass it through
            log.warn("Upstream returned error {} for {} {}", e.getStatusCode().value(), method, url);

            return UpstreamResult.builder()
                    .status(e.getStatusCode().value())
                    .body(e.getResponseBodyAsString())
                    .headers(flattenHeaders(e.getResponseHeaders()))
                    .success(false)
                    .build();

        } catch (ResourceAccessException e) {
            // Network error — upstream unreachable (timeout, DNS failure, etc.)
            log.error("Upstream unreachable: {} {} — {}", method, url, e.getMessage());

            return UpstreamResult.builder()
                    .status(502)
                    .body("{\"error\":\"Upstream unreachable\",\"message\":\"" + e.getMessage() + "\"}")
                    .headers(new HashMap<>())
                    .success(false)
                    .build();

        } catch (Exception e) {
            log.error("Unexpected error forwarding {} {}", method, url, e);

            return UpstreamResult.builder()
                    .status(500)
                    .body("{\"error\":\"Proxy internal error\",\"message\":\"" + e.getMessage() + "\"}")
                    .headers(new HashMap<>())
                    .success(false)
                    .build();
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private HttpHeaders buildHeaders(Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        if (headers != null) {
            headers.forEach(httpHeaders::set);
        }
        // Ensure JSON content type if not already set
        if (!httpHeaders.containsKey(HttpHeaders.CONTENT_TYPE)) {
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        }
        return httpHeaders;
    }

    private HttpMethod resolveMethod(String method) {
        return switch (method.toUpperCase()) {
            case "GET"    -> HttpMethod.GET;
            case "POST"   -> HttpMethod.POST;
            case "PUT"    -> HttpMethod.PUT;
            case "PATCH"  -> HttpMethod.PATCH;
            case "DELETE" -> HttpMethod.DELETE;
            case "HEAD"   -> HttpMethod.HEAD;
            case "OPTIONS"-> HttpMethod.OPTIONS;
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };
    }

    private Map<String, String> flattenHeaders(org.springframework.http.HttpHeaders headers) {
        Map<String, String> flat = new HashMap<>();
        if (headers == null) return flat;
        headers.forEach((key, values) -> {
            if (!values.isEmpty()) {
                flat.put(key, values.get(0));
            }
        });
        return flat;
    }
}