package com.alphaharvester.infrastructure.config;

import com.alphaharvester.domain.cache.MonthlyQuoteCacheEntry;
import com.alphaharvester.infrastructure.cache.MonthlyQuoteExpiryPolicy;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EhcacheConfig {

    private static final Logger log = LoggerFactory.getLogger(EhcacheConfig.class);

    @Bean(destroyMethod = "close")
    public CacheManager cacheManager() {
        log.info("Initializing in-memory Ehcache 3 for monthlyQuoteCache (heap: 2000 entries)");

        return CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("monthlyQuoteCache",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                String.class,
                                MonthlyQuoteCacheEntry.class,
                                ResourcePoolsBuilder.newResourcePoolsBuilder()
                                        .heap(2000, EntryUnit.ENTRIES)
                        )
                        .withExpiry(new MonthlyQuoteExpiryPolicy())
                )
                .build(true);
    }

    @Bean(destroyMethod = "")
    public Cache<String, MonthlyQuoteCacheEntry> monthlyQuoteCache(CacheManager cacheManager) {
        return cacheManager.getCache("monthlyQuoteCache", String.class, MonthlyQuoteCacheEntry.class);
    }
}

