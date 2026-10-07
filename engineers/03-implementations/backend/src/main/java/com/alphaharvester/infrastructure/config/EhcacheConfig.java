package com.alphaharvester.infrastructure.config;

import com.alphaharvester.domain.cache.MonthlyQuoteCacheEntry;
import com.alphaharvester.infrastructure.cache.MonthlyQuoteExpiryPolicy;
import org.ehcache.Cache;
import org.ehcache.PersistentCacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class EhcacheConfig {

    private static final Logger log = LoggerFactory.getLogger(EhcacheConfig.class);

    @Value("${app.cache.disk-dir:./target/ehcache-data}")
    private String diskDir;

    @Bean(destroyMethod = "close")
    public PersistentCacheManager persistentCacheManager() {
        File storageDir = new File(diskDir);
        if (!storageDir.exists()) {
            boolean created = storageDir.mkdirs();
            log.info("Created Ehcache disk persistence directory at {}: {}", diskDir, created);
        }

        log.info("Initializing Ehcache 3 with disk persistence directory: {}", storageDir.getAbsolutePath());

        PersistentCacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .with(CacheManagerBuilder.persistence(storageDir))
                .withCache("monthlyQuoteCache",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                String.class,
                                MonthlyQuoteCacheEntry.class,
                                ResourcePoolsBuilder.newResourcePoolsBuilder()
                                        .heap(2000, EntryUnit.ENTRIES)
                                        .disk(100, MemoryUnit.MB, true)
                        )
                        .withExpiry(new MonthlyQuoteExpiryPolicy())
                )
                .build(true);

        return cacheManager;
    }

    @Bean(destroyMethod = "")
    public Cache<String, MonthlyQuoteCacheEntry> monthlyQuoteCache(PersistentCacheManager persistentCacheManager) {
        return persistentCacheManager.getCache("monthlyQuoteCache", String.class, MonthlyQuoteCacheEntry.class);
    }
}
