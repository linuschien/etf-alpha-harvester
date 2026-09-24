package com.alphaharvester.adapter.out.persistence;

import com.alphaharvester.domain.entity.MarketDailyQuote;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface MarketDailyQuoteRepository extends R2dbcRepository<MarketDailyQuote, UUID>, ReactiveQueryByExampleExecutor<MarketDailyQuote> {
    Mono<MarketDailyQuote> findByTickerAndTradeDate(String ticker, LocalDateTime tradeDate);
    Flux<MarketDailyQuote> findByTickerAndTradeDateBetweenOrderByTradeDateAsc(String ticker, LocalDateTime startDate, LocalDateTime endDate);
    Flux<MarketDailyQuote> findByAssetIdOrderByTradeDateAsc(UUID assetId);
    Flux<MarketDailyQuote> findByBenchmarkIdOrderByTradeDateAsc(UUID benchmarkId);
    Flux<MarketDailyQuote> findByTickerOrderByTradeDateDesc(String ticker);
    Mono<MarketDailyQuote> findFirstByTickerOrderByTradeDateDesc(String ticker);
}

