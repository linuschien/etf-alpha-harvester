package com.alphaharvester.adapter.out.external;

import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwseMarketDataClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private TwseMarketDataClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        client = new TwseMarketDataClient(webClient, mapper);
    }

    @Test
    @DisplayName("Should fetch TWSE ETF master universe directly without MIS NAV and filter Stage 0 symbols")
    void shouldFetchTwseMasterUniverseDirectly() throws Exception {
        String json = """
                [
                  {
                    "出表日期": "1150921",
                    "基金代號": "0050",
                    "基金簡稱": "元大台灣50",
                    "基金中文名稱": "元大台灣卓越50證券投資信託基金",
                    "標的指數/追蹤指數名稱": "臺灣50指數",
                    "成立日期": "0920625",
                    "上市日期": "0920630",
                    "發行單位數/轉換數": "2205100000"
                  },
                  {
                    "出表日期": "1150921",
                    "基金代號": "00632R",
                    "基金簡稱": "元大台灣50反1",
                    "基金中文名稱": "元大台灣50單日反向1倍基金",
                    "標的指數/追蹤指數名稱": "臺灣50反向指數",
                    "成立日期": "1031020",
                    "上市日期": "1031031",
                    "發行單位數/轉換數": "5000000000"
                  }
                ]
                """;
        JsonNode node = mapper.readTree(json);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(json));

        StepVerifier.create(client.fetchEtfMasterUniverse())
                .assertNext(asset -> {
                    assertThat(asset.getTicker()).isEqualTo("0050");
                    assertThat(asset.getName()).isEqualTo("元大台灣卓越50證券投資信託基金");
                    assertThat(asset.getUnderlyingIndex()).isEqualTo("臺灣50指數");
                    // 0920630 -> 2003-06-30
                    assertThat(asset.getListingDate()).isEqualTo(LocalDateTime.of(2003, 6, 30, 0, 0));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fetch MIS NAV data and extract NAV, discount/premium, and shares")
    void shouldFetchMisNavData() throws Exception {
        String json = """
                {
                  "a1": [
                    {
                      "msgArray": [
                        {
                          "a": "0050",
                          "b": "元大台灣50",
                          "c": "2,205,100,000",
                          "f": "188.50",
                          "g": "-0.12"
                        }
                      ]
                    }
                  ]
                }
                """;
        JsonNode node = mapper.readTree(json);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(json));

        StepVerifier.create(client.fetchMisNavData())
                .assertNext(map -> {
                    assertThat(map).containsKey("0050");
                    var snap = map.get("0050");
                    assertThat(snap.name()).isEqualTo("元大台灣50");
                    assertThat(snap.nav()).isEqualByComparingTo(new BigDecimal("188.5000"));
                    assertThat(snap.discountPremiumPct()).isEqualByComparingTo(new BigDecimal("-0.1200"));
                    assertThat(snap.sharesOutstanding()).isEqualTo(2205100000L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fetch DCA rankings from TWSE and parse No, ETFsSecurityCode, and accounts")
    void shouldFetchDcaRankings() throws Exception {
        String json = """
                [
                  {
                    "No": "1",
                    "STOCKsSecurityCode": "2330",
                    "STOCKsName": "台積電",
                    "STOCKsNumberofTradingAccounts": "231711",
                    "ETFsSecurityCode": "0050",
                    "ETFsName": "元大台灣50",
                    "ETFsNumberofTradingAccounts": "1280028"
                  },
                  {
                    "No": "2",
                    "STOCKsSecurityCode": "2884",
                    "STOCKsName": "玉山金",
                    "STOCKsNumberofTradingAccounts": "27261",
                    "ETFsSecurityCode": "0056",
                    "ETFsName": "元大高股息",
                    "ETFsNumberofTradingAccounts": "338456"
                  }
                ]
                """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(json));

        StepVerifier.create(client.fetchDcaRankings(2026, 8))
                .assertNext(rank -> {
                    assertThat(rank.getRankPosition()).isEqualTo(1);
                    assertThat(rank.getTicker()).isEqualTo("0050");
                    assertThat(rank.getRegularInvestorCount()).isEqualTo(1280028);
                    assertThat(rank.getRankingYear()).isEqualTo(2026);
                    assertThat(rank.getRankingMonth()).isEqualTo(8);
                })
                .assertNext(rank -> {
                    assertThat(rank.getRankPosition()).isEqualTo(2);
                    assertThat(rank.getTicker()).isEqualTo("0056");
                    assertThat(rank.getRegularInvestorCount()).isEqualTo(338456);
                    assertThat(rank.getRankingYear()).isEqualTo(2026);
                    assertThat(rank.getRankingMonth()).isEqualTo(8);
                })
                .verifyComplete();
    }
}
