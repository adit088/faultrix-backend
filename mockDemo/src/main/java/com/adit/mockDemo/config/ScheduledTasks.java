package com.adit.mockDemo.config;

import com.adit.mockDemo.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasks {

    private final RateLimitFilter rateLimitFilter;

    /**
     * Clean up old rate limit entries every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupRateLimits() {
        log.debug("Running rate limiter cleanup task");
        rateLimitFilter.cleanup();
    }
}