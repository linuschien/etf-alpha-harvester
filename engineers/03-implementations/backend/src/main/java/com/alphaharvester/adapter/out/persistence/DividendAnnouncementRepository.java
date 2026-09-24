package com.alphaharvester.adapter.out.persistence;

import com.alphaharvester.domain.entity.DividendAnnouncement;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface DividendAnnouncementRepository extends R2dbcRepository<DividendAnnouncement, UUID>, ReactiveQueryByExampleExecutor<DividendAnnouncement> {
    Mono<DividendAnnouncement> findByTickerAndExDate(String ticker, LocalDateTime exDate);
    Flux<DividendAnnouncement> findByExDateBetweenOrderByExDateAsc(LocalDateTime startDate, LocalDateTime endDate);
    Flux<DividendAnnouncement> findByAssetIdOrderByExDateDesc(UUID assetId);
    Flux<DividendAnnouncement> findByTickerOrderByExDateDesc(String ticker);
}

