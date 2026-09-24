package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.GlobalAssetMetadataRepository;
import com.alphaharvester.adapter.out.persistence.MacroYieldSnapshotRepository;
import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.application.dto.GatekeeperReport;
import com.alphaharvester.application.service.DataCompletenessGatekeeperService;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.entity.MacroYieldSnapshot;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataCompletenessGatekeeperServiceTest {

    @Mock
    private GlobalAssetMetadataRepository metadataRepository;

    @Mock
    private MarketDailyQuoteRepository quoteRepository;

    @Mock
    private MacroYieldSnapshotRepository macroYieldRepository;

    private DataCompletenessGatekeeperService gatekeeperService;

    @BeforeEach
    void setUp() {
        gatekeeperService = new DataCompletenessGatekeeperService(metadataRepository, quoteRepository, macroYieldRepository);
    }

    @Test
    @DisplayName("Should PASS when all candidate ETF quotes and macro yield are present and valid")
    void shouldPassWhenAllDataComplete() {
        LocalDateTime now = LocalDateTime.now();
        MacroYieldSnapshot snapshot = new MacroYieldSnapshot(
                null, now, new BigDecimal("5.25"), new BigDecimal("4.20"), new BigDecimal("4.50"), new BigDecimal("0.10")
        );
        when(macroYieldRepository.findTopByOrderByRecordDateDesc()).thenReturn(Mono.just(snapshot));

        GlobalAssetMetadata asset = new GlobalAssetMetadata(
                UUID.randomUUID(), "0050", "元大台灣50", now.minusYears(10), "臺灣50",
                new BigDecimal("0.0043"), new BigDecimal("400000000000"),
                CandidateAssetClass.CORE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null
        );
        when(metadataRepository.findAll()).thenReturn(Flux.just(asset));

        MarketDailyQuote quote = new MarketDailyQuote();
        quote.setTicker("0050");
        quote.setClosePrice(new BigDecimal("190.0"));
        when(quoteRepository.findFirstByTickerOrderByTradeDateDesc("0050")).thenReturn(Mono.just(quote));

        StepVerifier.create(gatekeeperService.checkCompleteness())
                .assertNext(report -> {
                    assertThat(report.isPassed()).isTrue();
                    assertThat(report.status()).isEqualTo("PASS");
                    assertThat(report.macroYieldValid()).isTrue();
                    assertThat(report.checkedCandidatesCount()).isEqualTo(1);
                    assertThat(report.violations()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should HALT when macro yield snapshot is missing or out of [1%, 20%] range")
    void shouldHaltWhenMacroYieldInvalid() {
        LocalDateTime now = LocalDateTime.now();
        MacroYieldSnapshot abnormalSnapshot = new MacroYieldSnapshot(
                null, now, new BigDecimal("25.0"), new BigDecimal("4.20"), new BigDecimal("4.50"), new BigDecimal("0.10") // 25% > 20%
        );
        when(macroYieldRepository.findTopByOrderByRecordDateDesc()).thenReturn(Mono.just(abnormalSnapshot));
        when(metadataRepository.findAll()).thenReturn(Flux.empty());

        StepVerifier.create(gatekeeperService.checkCompleteness())
                .assertNext(report -> {
                    assertThat(report.isPassed()).isFalse();
                    assertThat(report.status()).isEqualTo("HALT");
                    assertThat(report.macroYieldValid()).isFalse();
                    assertThat(report.violations()).anyMatch(v -> v.contains("偏離合理區間"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should HALT when candidate ETF quote is missing or non-positive")
    void shouldHaltWhenQuoteMissingOrInvalid() {
        LocalDateTime now = LocalDateTime.now();
        MacroYieldSnapshot snapshot = new MacroYieldSnapshot(
                null, now, new BigDecimal("5.25"), new BigDecimal("4.20"), new BigDecimal("4.50"), new BigDecimal("0.10")
        );
        when(macroYieldRepository.findTopByOrderByRecordDateDesc()).thenReturn(Mono.just(snapshot));

        GlobalAssetMetadata asset = new GlobalAssetMetadata(
                UUID.randomUUID(), "0050", "元大台灣50", now.minusYears(10), "臺灣50",
                new BigDecimal("0.0043"), new BigDecimal("400000000000"),
                CandidateAssetClass.CORE, DistributionFrequency.SEMI_ANNUAL, 1, now, now, null
        );
        when(metadataRepository.findAll()).thenReturn(Flux.just(asset));
        when(quoteRepository.findFirstByTickerOrderByTradeDateDesc("0050")).thenReturn(Mono.empty()); // Missing quote

        StepVerifier.create(gatekeeperService.checkCompleteness())
                .assertNext(report -> {
                    assertThat(report.isPassed()).isFalse();
                    assertThat(report.status()).isEqualTo("HALT");
                    assertThat(report.violations()).anyMatch(v -> v.contains("缺失最新交易報價"));
                })
                .verifyComplete();
    }
}
