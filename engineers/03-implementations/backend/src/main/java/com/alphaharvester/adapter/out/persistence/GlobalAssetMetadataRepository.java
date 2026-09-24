package com.alphaharvester.adapter.out.persistence;

import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.model.CandidateAssetClass;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface GlobalAssetMetadataRepository extends R2dbcRepository<GlobalAssetMetadata, UUID>, ReactiveQueryByExampleExecutor<GlobalAssetMetadata> {
    Mono<GlobalAssetMetadata> findByTicker(String ticker);
    Flux<GlobalAssetMetadata> findByAssetClass(CandidateAssetClass assetClass);
}

