package com.alphaharvester.adapter.out.persistence;

import com.alphaharvester.domain.entity.DcaPopularityRank;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface DcaPopularityRankRepository extends R2dbcRepository<DcaPopularityRank, UUID>, ReactiveQueryByExampleExecutor<DcaPopularityRank> {
    Mono<DcaPopularityRank> findByTickerAndRankingYearAndRankingMonth(String ticker, Integer rankingYear, Integer rankingMonth);
    Flux<DcaPopularityRank> findByRankingYearAndRankingMonthOrderByRankPositionAsc(Integer rankingYear, Integer rankingMonth);
    Flux<DcaPopularityRank> findByAssetIdOrderByRankingYearDescRankingMonthDesc(UUID assetId);
}
