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

    @Test
    @DisplayName("Should strictly intersect series dates and not forward-fill when T10Y2Y has an extra observation")
    void shouldStrictlyIntersectDatesWithoutForwardFilling() {
        LocalDate d1 = LocalDate.of(2026, 9, 22);
        LocalDate d2 = LocalDate.of(2026, 9, 23);

        java.util.Map<LocalDate, BigDecimal> corp = java.util.Map.of(d1, new BigDecimal("5.69"));
        java.util.Map<LocalDate, BigDecimal> y10 = java.util.Map.of(d1, new BigDecimal("4.96"));
        java.util.Map<LocalDate, BigDecimal> y20 = java.util.Map.of(d1, new BigDecimal("5.33"));
        java.util.Map<LocalDate, BigDecimal> spread = java.util.Map.of(
                d1, new BigDecimal("0.25"),
                d2, new BigDecimal("0.28") // d2 only present in spread
        );

        var snapshots = client.assembleAlignedSnapshots(corp, y10, y20, spread);
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).getRecordDate().toLocalDate()).isEqualTo(d1);
        assertThat(snapshots.get(0).getUsCorporateBondEffectiveYield()).isEqualByComparingTo(new BigDecimal("5.69"));
        assertThat(snapshots.get(0).getYieldSpread10yMinus2y()).isEqualByComparingTo(new BigDecimal("0.25"));
    }

    @Test
    @DisplayName("Should return empty list if any input map is null in assembleAlignedSnapshots")
    void shouldReturnEmptyIfAnyMapNull() {
        assertThat(client.assembleAlignedSnapshots(null, java.util.Map.of(), java.util.Map.of(), java.util.Map.of())).isEmpty();
    }
}
