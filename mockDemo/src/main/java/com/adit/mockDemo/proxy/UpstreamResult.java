package com.adit.mockDemo.proxy;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Internal result from UpstreamForwarder.
 * Not serialized — only used inside ProxyChaosService.
 */
@Getter
@Builder
public class UpstreamResult {
    private final int status;
    private final String body;
    private final Map<String, String> headers;
    private final boolean success;
}