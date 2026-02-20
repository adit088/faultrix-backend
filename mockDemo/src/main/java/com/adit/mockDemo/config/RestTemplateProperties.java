package com.adit.mockDemo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rest.template")
public class RestTemplateProperties {
    private int connectTimeout = 3000;
    private int readTimeout = 3000;
}