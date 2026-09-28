package com.alphaharvester.adapter.out.persistence;

import com.alphaharvester.domain.entity.GlobalAssetPairwiseMatrix;
import com.alphaharvester.domain.model.CandidateAssetClass;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface GlobalAssetPairwiseMatrixRepository extends R2dbcRepository<GlobalAssetPairwiseMatrix, UUID>, ReactiveQueryByExampleExecutor<GlobalAssetPairwiseMatrix> {
    Flux<GlobalAssetPairwiseMatrix> findByEvaluationDateAndAssetClass(LocalDateTime evaluationDate, CandidateAssetClass assetClass);
    Flux<GlobalAssetPairwiseMatrix> findByEvaluationDateAndAssetClassAndBaseTicker(LocalDateTime evaluationDate, CandidateAssetClass assetClass, String baseTicker);
    Mono<Void> deleteByEvaluationDate(LocalDateTime evaluationDate);
}
