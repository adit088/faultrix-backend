package com.adit.mockDemo.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ntropi.chaos")
public class NtropiChaosProperties {

    private double failureRate;
    private long maxDelayMs;
    private long seed;
}
