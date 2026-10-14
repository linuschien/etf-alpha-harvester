package com.alphaharvester.infrastructure.config;

import com.alphaharvester.domain.cache.MonthlyQuoteCacheEntry;
import com.alphaharvester.infrastructure.cache.MonthlyQuoteDbCacheLoaderWriter;
import com.alphaharvester.infrastructure.cache.MonthlyQuoteExpiryPolicy;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.builders.WriteBehindConfigurationBuilder;
import org.ehcache.config.units.EntryUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EhcacheConfig {

    private static final Logger log = LoggerFactory.getLogger(EhcacheConfig.class);

    @Bean(destroyMethod = "close")
    public CacheManager cacheManager(MonthlyQuoteDbCacheLoaderWriter loaderWriter) {
        log.info("Initializing Ehcache 3 for monthlyQuoteCache with DB Persistence (heap: 2000 entries, concurrencyLevel=2, queueSize=32)");

        return CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("monthlyQuoteCache",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                String.class,
                                MonthlyQuoteCacheEntry.class,
                                ResourcePoolsBuilder.newResourcePoolsBuilder()
                                        .heap(2000, EntryUnit.ENTRIES)
                        )
                        .withExpiry(new MonthlyQuoteExpiryPolicy())
                        .withLoaderWriter(loaderWriter)
                        .add(WriteBehindConfigurationBuilder.newUnBatchedWriteBehindConfiguration()
                                .concurrencyLevel(2)
                                .queueSize(32))
                )
                .build(true);
    }

    @Bean(destroyMethod = "")
    public Cache<String, MonthlyQuoteCacheEntry> monthlyQuoteCache(CacheManager cacheManager) {
        return cacheManager.getCache("monthlyQuoteCache", String.class, MonthlyQuoteCacheEntry.class);
    }
}
