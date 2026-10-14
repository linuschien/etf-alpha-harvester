package com.alphaharvester.infrastructure.cache;

import com.alphaharvester.domain.cache.MonthlyQuoteCacheEntry;
import org.ehcache.spi.loaderwriter.CacheLoaderWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import java.util.List;

/**
 * Ehcache 3 CacheLoaderWriter SPI implementation for MonthlyQuoteCacheEntry.
 * Provides Read-Through and Write-Behind database persistence to H2 via JdbcTemplate.
 */
@Component
public class MonthlyQuoteDbCacheLoaderWriter implements CacheLoaderWriter<String, MonthlyQuoteCacheEntry> {

    private static final Logger log = LoggerFactory.getLogger(MonthlyQuoteDbCacheLoaderWriter.class);

    private static final String SELECT_SQL = "SELECT payload_data FROM monthly_quote_cache WHERE cache_key = ?";
    private static final String MERGE_SQL = "MERGE INTO monthly_quote_cache (cache_key, payload_data) KEY (cache_key) VALUES (?, ?)";
    private static final String DELETE_SQL = "DELETE FROM monthly_quote_cache WHERE cache_key = ?";

    private final JdbcTemplate jdbcTemplate;

    public MonthlyQuoteDbCacheLoaderWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public MonthlyQuoteCacheEntry load(String key) {
        log.debug("DB Cache Read-Through loading key: {}", key);
        try {
            byte[] payload = jdbcTemplate.queryForObject(SELECT_SQL, byte[].class, key);
            return payload != null ? (MonthlyQuoteCacheEntry) SerializationUtils.deserialize(payload) : null;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public void write(String key, MonthlyQuoteCacheEntry value) {
        if (value == null) {
            delete(key);
            return;
        }
        log.debug("DB Cache Write-Behind writing key: {}", key);
        byte[] payload = SerializationUtils.serialize(value);
        jdbcTemplate.update(MERGE_SQL, key, payload);
    }

    @Override
    public void delete(String key) {
        log.debug("DB Cache deleting key: {}", key);
        jdbcTemplate.update(DELETE_SQL, key);
    }
}

