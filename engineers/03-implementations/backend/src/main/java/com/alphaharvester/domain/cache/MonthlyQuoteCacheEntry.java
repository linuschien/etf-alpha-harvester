package com.alphaharvester.domain.cache;

import com.alphaharvester.domain.entity.MarketDailyQuote;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cache entry representing all enriched daily quotes for a single ticker in a single calendar month.
 * Stored in Ehcache with key pattern "{ticker}:{YYYY-MM}".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyQuoteCacheEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ticker;
    private String yearMonth; // "YYYY-MM"
    private LocalDateTime latestTradeDate;
    private List<MarketDailyQuote> quotes;
}
