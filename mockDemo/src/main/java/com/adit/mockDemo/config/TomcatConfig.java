package com.adit.mockDemo.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> factory.addConnectorCustomizers((Connector connector) -> {
            // Allow common bracket/pipe chars in path/query strings
            // encodedSolidusHandling REMOVED — was a path traversal security risk.
            // InsightController now uses ?target= query param, so %2F in paths is no longer needed.
            connector.setProperty("relaxedPathChars", "[]|");
            connector.setProperty("relaxedQueryChars", "[]|{}^`<>\"");
        });
    }
}