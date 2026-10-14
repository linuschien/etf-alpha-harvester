package com.alphaharvester.infrastructure.cache;

import com.alphaharvester.domain.cache.MonthlyQuoteCacheEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.SerializationUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyQuoteDbCacheLoaderWriterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MonthlyQuoteDbCacheLoaderWriter loaderWriter;

    @BeforeEach
    void setUp() {
        loaderWriter = new MonthlyQuoteDbCacheLoaderWriter(jdbcTemplate);
    }

    @Test
    @DisplayName("load() should deserialize and return entry when record exists in DB")
    void shouldLoadRecordWhenFound() {
        String key = "0050:2026-09";
        MonthlyQuoteCacheEntry expectedEntry = MonthlyQuoteCacheEntry.builder()
                .ticker("0050")
                .yearMonth("2026-09")
                .quotes(Collections.emptyList())
                .build();
        byte[] payload = SerializationUtils.serialize(expectedEntry);

        when(jdbcTemplate.queryForObject(any(String.class), eq(byte[].class), eq(key)))
                .thenReturn(payload);

        MonthlyQuoteCacheEntry actual = loaderWriter.load(key);

        assertThat(actual).isNotNull();
        assertThat(actual.getTicker()).isEqualTo("0050");
        assertThat(actual.getYearMonth()).isEqualTo("2026-09");
    }

    @Test
    @DisplayName("load() should return null when no record exists in DB (EmptyResultDataAccessException)")
    void shouldReturnNullWhenNotFound() {
        String key = "0050:2026-09";
        when(jdbcTemplate.queryForObject(any(String.class), eq(byte[].class), eq(key)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        MonthlyQuoteCacheEntry actual = loaderWriter.load(key);

        assertThat(actual).isNull();
    }

    @Test
    @DisplayName("load() should return null when payload byte array is null")
    void shouldReturnNullWhenPayloadIsNull() {
        String key = "0050:2026-09";
        when(jdbcTemplate.queryForObject(any(String.class), eq(byte[].class), eq(key)))
                .thenReturn(null);

        MonthlyQuoteCacheEntry actual = loaderWriter.load(key);

        assertThat(actual).isNull();
    }

    @Test
    @DisplayName("write() should serialize and execute MERGE INTO SQL")
    void shouldSerializeAndWriteRecord() {
        String key = "0050:2026-09";
        MonthlyQuoteCacheEntry entry = MonthlyQuoteCacheEntry.builder()
                .ticker("0050")
                .yearMonth("2026-09")
                .quotes(Collections.emptyList())
                .build();

        loaderWriter.write(key, entry);

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(jdbcTemplate).update(
                eq("MERGE INTO monthly_quote_cache (cache_key, payload_data) KEY (cache_key) VALUES (?, ?)"),
                eq(key),
                payloadCaptor.capture()
        );

        byte[] capturedBytes = payloadCaptor.getValue();
        assertThat(capturedBytes).isNotNull();
        MonthlyQuoteCacheEntry deserialized = (MonthlyQuoteCacheEntry) SerializationUtils.deserialize(capturedBytes);
        assertThat(deserialized.getTicker()).isEqualTo("0050");
        assertThat(deserialized.getYearMonth()).isEqualTo("2026-09");
    }

    @Test
    @DisplayName("write() with null value should trigger delete()")
    void shouldDeleteWhenValueIsNull() {
        String key = "0050:2026-09";

        loaderWriter.write(key, null);

        verify(jdbcTemplate).update(
                eq("DELETE FROM monthly_quote_cache WHERE cache_key = ?"),
                eq(key)
        );
    }

    @Test
    @DisplayName("delete() should execute DELETE SQL")
    void shouldDeleteRecord() {
        String key = "0050:2026-09";

        loaderWriter.delete(key);

        verify(jdbcTemplate).update(
                eq("DELETE FROM monthly_quote_cache WHERE cache_key = ?"),
                eq(key)
        );
    }
}

