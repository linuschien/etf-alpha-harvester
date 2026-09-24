package com.alphaharvester.adapter.out.persistence;

import com.alphaharvester.domain.entity.GlobalAssetScore;
import com.alphaharvester.domain.model.CandidateAssetClass;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface GlobalAssetScoreRepository extends R2dbcRepository<GlobalAssetScore, UUID>, ReactiveQueryByExampleExecutor<GlobalAssetScore> {
    Mono<GlobalAssetScore> findByTickerAndEvaluationDate(String ticker, LocalDateTime evaluationDate);
    Flux<GlobalAssetScore> findByAssetClassAndEvaluationDateOrderByClassRankAsc(CandidateAssetClass assetClass, LocalDateTime evaluationDate);
    Flux<GlobalAssetScore> findByEvaluationDateOrderByClassRankAsc(LocalDateTime evaluationDate);
}

