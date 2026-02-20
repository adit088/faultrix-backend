package com.adit.mockDemo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@Slf4j
public class AsyncConfig {

    @Bean(name = "chaosAsyncExecutor")
    public Executor chaosAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("chaos-async-");
        executor.setRejectedExecutionHandler((r, e) ->
                log.error("Chaos async queue full — dropping task. " +
                        "Consider increasing queueCapacity or maxPoolSize."));
        executor.initialize();

        log.info("Chaos async executor initialized: core={}, max={}, queue={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());

        return executor;
    }
}