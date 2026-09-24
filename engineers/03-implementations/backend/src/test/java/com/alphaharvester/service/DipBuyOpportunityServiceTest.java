package com.alphaharvester.service;

import com.alphaharvester.adapter.out.persistence.MarketDailyQuoteRepository;
import com.alphaharvester.application.dto.DipBuyOpportunityScore;
import com.alphaharvester.application.service.DipBuyOpportunityService;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DipBuyOpportunityServiceTest {

    @Mock
    private MarketDailyQuoteRepository quoteRepository;

    private DipBuyOpportunityService service;

    @BeforeEach
    void setUp() {
        service = new DipBuyOpportunityService(quoteRepository);
    }

    @Test
    @DisplayName("Should return baseline low-star score when quotes are empty")
    void shouldReturnBaselineWhenQuotesEmpty() {
        DipBuyOpportunityScore score = service.evaluatePure("0050", List.of(), 15.0);

        assertThat(score.ticker()).isEqualTo("0050");
        assertThat(score.compositeScore()).isEqualTo(35.0);
        assertThat(score.starRating()).contains("低星觀望區");
    }

    @Test
    @DisplayName("Should evaluate 5-star golden dip when all four dimensions align")
    void shouldEvaluateFiveStarGoldenDip() {
        // Construct 60 quotes where current price is severely collapsed (below BB lower, drawdown >= 38.2%, below 240MA)
        List<MarketDailyQuote> quotes = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now();

        // 52-week high is 200.0, current price is 110.0 (Drawdown = -45% >= 38.2%)
        quotes.add(new MarketDailyQuote(null, null, null, "0050", baseTime,
                new BigDecimal("112.0"), new BigDecimal("113.0"), new BigDecimal("109.0"),
                new BigDecimal("110.0"), 1000000L, new BigDecimal("110000000"), null, null));

        // Prior 30 quotes around 170.0 ~ 200.0 to create wide BB and high MAs
        for (int i = 1; i <= 60; i++) {
            quotes.add(new MarketDailyQuote(null, null, null, "0050", baseTime.minusDays(i),
                    new BigDecimal("180.0"), new BigDecimal("200.0"), new BigDecimal("175.0"),
                    new BigDecimal("185.0"), 1000000L, new BigDecimal("185000000"), null, null));
        }

        // VIX = 36.0 (Panic >= 35 -> 20 pts)
        DipBuyOpportunityScore score = service.evaluatePure("0050", quotes, 36.0);

        assertThat(score.compositeScore()).isGreaterThanOrEqualTo(80.0);
        assertThat(score.starRating()).contains("五星黃金坑");
        assertThat(score.winRateEstimate()).contains("≥ 90%");
        assertThat(score.recommendation()).contains("調用交割戶停利閒置資金");
        assertThat(score.bollingerScore()).isEqualTo(30.0);
        assertThat(score.fibonacciScore()).isEqualTo(25.0);
        assertThat(score.panicScore()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("Should evaluate 3-star normal accumulation on moderate pullback")
    void shouldEvaluateThreeStarNormalAccumulation() {
        List<MarketDailyQuote> quotes = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now();

        // High 190.0, current 175.0 (Drawdown ~ -7.8% < 14.6% -> 0 pts)
        quotes.add(new MarketDailyQuote(null, null, null, "0050", baseTime,
                new BigDecimal("176.0"), new BigDecimal("178.0"), new BigDecimal("174.0"),
                new BigDecimal("175.0"), 1000000L, new BigDecimal("175000000"), null, null));

        for (int i = 1; i <= 30; i++) {
            quotes.add(new MarketDailyQuote(null, null, null, "0050", baseTime.minusDays(i),
                    new BigDecimal("180.0"), new BigDecimal("190.0"), new BigDecimal("178.0"),
                    new BigDecimal("180.0"), 1000000L, new BigDecimal("180000000"), null, null));
        }

        // VIX = 26.0 (8 pts)
        DipBuyOpportunityScore score = service.evaluatePure("0050", quotes, 26.0);

        assertThat(score.compositeScore()).isLessThan(60.0);
        assertThat(score.recommendation()).isNotEmpty();
    }

    @Test
    @DisplayName("Should execute calculateDipBuyOpportunity reactive stream successfully")
    void shouldExecuteCalculateDipBuyOpportunity() {
        LocalDateTime baseTime = LocalDateTime.now();
        MarketDailyQuote quote = new MarketDailyQuote(null, null, null, "0050", baseTime,
                new BigDecimal("180.0"), new BigDecimal("185.0"), new BigDecimal("178.0"),
                new BigDecimal("182.0"), 1000000L, new BigDecimal("182000000"), null, null);

        MarketDailyQuote vixQuote = new MarketDailyQuote(null, null, null, "^VIX", baseTime,
                new BigDecimal("21.0"), new BigDecimal("23.0"), new BigDecimal("20.5"),
                new BigDecimal("22.5"), 0L, BigDecimal.ZERO, null, null);

        when(quoteRepository.findByTickerOrderByTradeDateDesc("0050")).thenReturn(Flux.just(quote));
        when(quoteRepository.findByTickerOrderByTradeDateDesc("^VIX")).thenReturn(Flux.just(vixQuote));

        StepVerifier.create(service.calculateDipBuyOpportunity("0050"))
                .assertNext(score -> {
                    assertThat(score.ticker()).isEqualTo("0050");
                    assertThat(score.compositeScore()).isGreaterThan(0.0);
                })
                .verifyComplete();
    }
}

