package com.alphaharvester.domain.callback;

import com.alphaharvester.domain.entity.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Configuration
public class R2dbcEntityCallbacksConfig {

    @Bean
    public BeforeConvertCallback<GlobalAssetMetadata> globalAssetMetadataCallback() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(LocalDateTime.now());
            }
            if (entity.getUpdatedAt() == null) {
                entity.setUpdatedAt(LocalDateTime.now());
            }
            return Mono.just(entity);
        };
    }

    @Bean
    public BeforeConvertCallback<BenchmarkIndex> benchmarkIndexCallback() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(LocalDateTime.now());
            }
            if (entity.getUpdatedAt() == null) {
                entity.setUpdatedAt(LocalDateTime.now());
            }
            return Mono.just(entity);
        };
    }

    @Bean
    public BeforeConvertCallback<MarketDailyQuote> marketDailyQuoteCallback() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return Mono.just(entity);
        };
    }

    @Bean
    public BeforeConvertCallback<MacroYieldSnapshot> macroYieldSnapshotCallback() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return Mono.just(entity);
        };
    }

    @Bean
    public BeforeConvertCallback<GlobalAssetScore> globalAssetScoreCallback() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return Mono.just(entity);
        };
    }

    @Bean
    public BeforeConvertCallback<DcaPopularityRank> dcaPopularityRankCallback() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return Mono.just(entity);
        };
    }

    @Bean
    public BeforeConvertCallback<DividendAnnouncement> dividendAnnouncementCallback() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return Mono.just(entity);
        };
    }

    @Bean
    public BeforeConvertCallback<CorporateAction> corporateActionCallback() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return Mono.just(entity);
        };
    }
}

