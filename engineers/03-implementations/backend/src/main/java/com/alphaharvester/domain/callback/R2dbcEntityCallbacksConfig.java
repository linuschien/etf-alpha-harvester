package com.alphaharvester.domain.callback;

import com.alphaharvester.domain.entity.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Configuration
public class R2dbcEntityCallbacksConfig {

    @Bean
    public BeforeConvertCallback<GlobalAssetMetadata> globalAssetMetadataCallback() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                if (entity.getTicker() != null && !entity.getTicker().isBlank()) {
                    entity.setId(UUID.nameUUIDFromBytes(
                            ("ALPHA-ETF:" + entity.getTicker().trim()).getBytes(StandardCharsets.UTF_8)
                    ));
                } else {
                    entity.setId(UUID.randomUUID());
                }
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

    private static final java.util.Map<String, UUID> BENCHMARK_ID_MAP = java.util.Map.of(
            "^TWII", UUID.fromString("b0000001-0000-0000-0000-000000000001"),
            "^GSPC", UUID.fromString("b0000001-0000-0000-0000-000000000002"),
            "^NDX", UUID.fromString("b0000001-0000-0000-0000-000000000003"),
            "^SOX", UUID.fromString("b0000001-0000-0000-0000-000000000004"),
            "^N225", UUID.fromString("b0000001-0000-0000-0000-000000000005"),
            "^VIX", UUID.fromString("b0000001-0000-0000-0000-000000000006"),
            "^VXN", UUID.fromString("b0000001-0000-0000-0000-000000000007"),
            "^MOVE", UUID.fromString("b0000001-0000-0000-0000-000000000008"),
            "FEAR_GREED", UUID.fromString("b0000001-0000-0000-0000-000000000009")
    );

    @Bean
    public BeforeConvertCallback<MarketDailyQuote> marketDailyQuoteCallback() {
        return (entity, table) -> {
            if (entity.getId() == null && entity.getTicker() != null && entity.getTradeDate() != null) {
                entity.setId(UUID.nameUUIDFromBytes(
                        ("ALPHA-QUOTE:" + entity.getTicker().trim() + ":" + entity.getTradeDate().toLocalDate()).getBytes(StandardCharsets.UTF_8)
                ));
            } else if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }

            if (entity.getTicker() != null) {
                String ticker = entity.getTicker().trim();
                if (BENCHMARK_ID_MAP.containsKey(ticker)) {
                    if (entity.getBenchmarkId() == null) {
                        entity.setBenchmarkId(BENCHMARK_ID_MAP.get(ticker));
                    }
                } else if (!ticker.startsWith("^")) {
                    if (entity.getAssetId() == null) {
                        entity.setAssetId(UUID.nameUUIDFromBytes(
                                ("ALPHA-ETF:" + ticker).getBytes(StandardCharsets.UTF_8)
                        ));
                    }
                }
            }
            return Mono.just(entity);
        };
    }

    @Bean
    public BeforeConvertCallback<MacroYieldSnapshot> macroYieldSnapshotCallback() {
        return (entity, table) -> {
            if (entity.getId() == null && entity.getRecordDate() != null) {
                entity.setId(UUID.nameUUIDFromBytes(
                        ("ALPHA-MACRO-YIELD:" + entity.getRecordDate().toLocalDate()).getBytes(StandardCharsets.UTF_8)
                ));
            } else if (entity.getId() == null) {
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
                if (entity.getTicker() != null && entity.getRankingYear() != null && entity.getRankingMonth() != null) {
                    entity.setId(UUID.nameUUIDFromBytes(("DCA:" + entity.getTicker() + ":" + entity.getRankingYear() + ":" + entity.getRankingMonth()).getBytes(StandardCharsets.UTF_8)));
                } else {
                    entity.setId(UUID.randomUUID());
                }
            }
            return Mono.just(entity);
        };
    }

    @Bean
    public BeforeConvertCallback<DividendAnnouncement> dividendAnnouncementCallback() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                if (entity.getTicker() != null && entity.getExDate() != null) {
                    entity.setId(UUID.nameUUIDFromBytes(("DIV:" + entity.getTicker() + ":" + entity.getExDate().toLocalDate()).getBytes(StandardCharsets.UTF_8)));
                } else {
                    entity.setId(UUID.randomUUID());
                }
            }
            return Mono.just(entity);
        };
    }

    @Bean
    public BeforeConvertCallback<CorporateAction> corporateActionCallback() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                if (entity.getTicker() != null && entity.getEffectiveDate() != null) {
                    entity.setId(UUID.nameUUIDFromBytes(("SPLIT:" + entity.getTicker() + ":" + entity.getEffectiveDate().toLocalDate()).getBytes(StandardCharsets.UTF_8)));
                } else {
                    entity.setId(UUID.randomUUID());
                }
            }
            return Mono.just(entity);
        };
    }
}

