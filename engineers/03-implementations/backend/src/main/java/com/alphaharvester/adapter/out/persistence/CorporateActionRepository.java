package com.alphaharvester.adapter.out.persistence;

import com.alphaharvester.domain.entity.CorporateAction;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface CorporateActionRepository extends R2dbcRepository<CorporateAction, UUID>, ReactiveQueryByExampleExecutor<CorporateAction> {
    Mono<CorporateAction> findByTickerAndEffectiveDate(String ticker, LocalDateTime effectiveDate);
    Flux<CorporateAction> findByEffectiveDateBetweenOrderByEffectiveDateAsc(LocalDateTime startDate, LocalDateTime endDate);
    Flux<CorporateAction> findByAssetIdOrderByEffectiveDateDesc(UUID assetId);
    Flux<CorporateAction> findByTickerOrderByEffectiveDateDesc(String ticker);
}

