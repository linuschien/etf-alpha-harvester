package com.alphaharvester.adapter.out.persistence;

import com.alphaharvester.domain.entity.MacroYieldSnapshot;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface MacroYieldSnapshotRepository extends R2dbcRepository<MacroYieldSnapshot, UUID>, ReactiveQueryByExampleExecutor<MacroYieldSnapshot> {
    Mono<MacroYieldSnapshot> findByRecordDate(LocalDateTime recordDate);
    Mono<MacroYieldSnapshot> findTopByOrderByRecordDateDesc();
    Flux<MacroYieldSnapshot> findByRecordDateBetweenOrderByRecordDateAsc(LocalDateTime startDate, LocalDateTime endDate);
}

