package com.adit.mockDemo.proxy;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * Request body sent by the client to the Faultrix chaos proxy.
 *
 * The client routes their outbound HTTP calls through:
 *   POST /api/v1/proxy/forward
 *
 * Body example:
 * {
 *   "method":  "GET",
 *   "url":     "https://api.stripe.com/v1/charges",
 *   "headers": { "Authorization": "Bearer sk_live_xxx" },
 *   "body":    null
 * }
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProxyRequest {

    /** HTTP method: GET, POST, PUT, PATCH, DELETE */
    @NotBlank(message = "method is required")
    private String method;

    /** Full upstream URL to forward the request to */
    @NotBlank(message = "url is required")
    private String url;

    /** Optional headers to forward upstream (Authorization, Content-Type, etc.) */
    private Map<String, String> headers;

    /** Optional request body (for POST/PUT/PATCH) */
    private String body;
}