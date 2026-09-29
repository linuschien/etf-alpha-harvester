package com.alphaharvester.adapter.out.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FredPublicMarketDataClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private FredPublicMarketDataClient client;

    @BeforeEach
    void setUp() {
        client = new FredPublicMarketDataClient(webClient);
    }

    @Test
    @DisplayName("Should correctly parse latest observation ignoring trailing dot '.' entries")
    void shouldParseLatestObservationIgnoringDots() {
        String csv = """
                observation_date,BAMLC0A0CMEY
                2026-09-18,5.65
                2026-09-19,.
                2026-09-20,.
                2026-09-21,.
                2026-09-22,5.69
                2026-09-23,.
                """;

        Optional<FredPublicMarketDataClient.Observation> obs = client.parseLatestObservationFromCsv(csv);
        assertThat(obs).isPresent();
        assertThat(obs.get().date()).isEqualTo(LocalDate.of(2026, 9, 22));
        assertThat(obs.get().value()).isEqualByComparingTo(new BigDecimal("5.69"));
    }

    @Test
    @DisplayName("Should return empty when CSV has no valid data rows")
    void shouldReturnEmptyForEmptyOrInvalidCsv() {
        assertThat(client.parseLatestObservationFromCsv("")).isEmpty();
        assertThat(client.parseLatestObservationFromCsv(null)).isEmpty();
        assertThat(client.parseLatestObservationFromCsv("observation_date,BAMLC0A0CMEY\n2026-09-22,.")).isEmpty();
    }

    @Test
    @DisplayName("Should fetch and assemble macro yield snapshot from FRED series endpoints")
    void shouldFetchLatestMacroYield() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        String bamlcCsv = "observation_date,BAMLC0A0CMEY\n2026-09-22,5.69\n";
        String dgs10Csv = "observation_date,DGS10\n2026-09-22,4.96\n";
        String dgs20Csv = "observation_date,DGS20\n2026-09-22,5.33\n";
        String t10y2yCsv = "observation_date,T10Y2Y\n2026-09-22,0.25\n";

        when(responseSpec.bodyToMono(String.class)).thenReturn(
                Mono.just(bamlcCsv),
                Mono.just(dgs10Csv),
                Mono.just(dgs20Csv),
                Mono.just(t10y2yCsv)
        );

        StepVerifier.create(client.fetchLatestMacroYield())
                .assertNext(snapshot -> {
                    assertThat(snapshot.getUsCorporateBondEffectiveYield()).isEqualByComparingTo(new BigDecimal("5.69"));
                    assertThat(snapshot.getUs10YearTreasuryYield()).isEqualByComparingTo(new BigDecimal("4.96"));
                    assertThat(snapshot.getUs20YearTreasuryYield()).isEqualByComparingTo(new BigDecimal("5.33"));
                    assertThat(snapshot.getYieldSpread10yMinus2y()).isEqualByComparingTo(new BigDecimal("0.25"));
                    assertThat(snapshot.getRecordDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 22));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should parse all valid observations from CSV into Map")
    void shouldParseAllObservationsFromCsv() {
        String csv = """
                observation_date,DGS10
                2026-09-21,4.96
                2026-09-22,4.96
                2026-09-23,.
                2026-09-24,5.18
                """;
        var map = client.parseAllObservationsFromCsv(csv);
        assertThat(map).hasSize(3);
        assertThat(map.get(LocalDate.of(2026, 9, 21))).isEqualByComparingTo(new BigDecimal("4.96"));
        assertThat(map.get(LocalDate.of(2026, 9, 22))).isEqualByComparingTo(new BigDecimal("4.96"));
        assertThat(map.get(LocalDate.of(2026, 9, 24))).isEqualByComparingTo(new BigDecimal("5.18"));
    }

    @Test
    @DisplayName("Should fetch and assemble historical macro yields from FRED")
    void shouldFetchHistoricalMacroYields() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        String bamlcCsv = "observation_date,BAMLC0A0CMEY\n2026-09-22,5.69\n2026-09-23,5.83\n";
        String dgs10Csv = "observation_date,DGS10\n2026-09-22,4.96\n2026-09-23,5.11\n";
        String dgs20Csv = "observation_date,DGS20\n2026-09-22,5.33\n2026-09-23,5.45\n";
        String t10y2yCsv = "observation_date,T10Y2Y\n2026-09-22,0.25\n2026-09-23,0.26\n";

        when(responseSpec.bodyToMono(String.class)).thenReturn(
                Mono.just(bamlcCsv),
                Mono.just(dgs10Csv),
                Mono.just(dgs20Csv),
                Mono.just(t10y2yCsv)
        );

        StepVerifier.create(client.fetchHistoricalMacroYields(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23)))
                .assertNext(s1 -> {
                    assertThat(s1.getRecordDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 22));
                    assertThat(s1.getUsCorporateBondEffectiveYield()).isEqualByComparingTo(new BigDecimal("5.69"));
                })
                .assertNext(s2 -> {
                    assertThat(s2.getRecordDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 23));
                    assertThat(s2.getUsCorporateBondEffectiveYield()).isEqualByComparingTo(new BigDecimal("5.83"));
                })
                .verifyComplete();
    }
}
