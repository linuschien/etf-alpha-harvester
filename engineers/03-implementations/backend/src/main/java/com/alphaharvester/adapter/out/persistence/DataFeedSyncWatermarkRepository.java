package com.alphaharvester.adapter.out.persistence;

import com.alphaharvester.domain.entity.DataFeedSyncWatermark;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface DataFeedSyncWatermarkRepository extends R2dbcRepository<DataFeedSyncWatermark, UUID> {
    Mono<DataFeedSyncWatermark> findByFeedName(String feedName);
}

