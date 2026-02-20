package com.adit.mockDemo.observability;

import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {
    // Metrics are now created inline in the services that use them
    // This avoids Spring bean injection ambiguity issues
}