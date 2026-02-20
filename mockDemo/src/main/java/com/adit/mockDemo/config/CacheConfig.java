package com.adit.mockDemo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration using Caffeine.
 *
 * Replaced ConcurrentMapCacheManager (no TTL — entries lived forever) with
 * CaffeineCacheManager configured with a 5-minute expireAfterWrite.
 * This prevents the cache serving stale chaos rules if the DB is edited
 * directly (e.g. via migration, admin tool, or another service instance).
 *
 * CacheEvict annotations in ChaosRuleService still work normally — Caffeine
 * honours Spring's cache abstraction eviction calls in addition to its own TTL.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** TTL for all cache entries: 5 minutes after write. */
    private static final long CACHE_TTL_MINUTES = 5;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("chaosRules");
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
                        .maximumSize(1_000)   // guard against unbounded growth
                        .recordStats()        // exposes hit/miss to Micrometer automatically
        );
        return manager;
    }
}