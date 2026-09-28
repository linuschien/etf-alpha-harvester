package com.alphaharvester.adapter.out.external;

import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.entity.MarketDailyQuote;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TpexMarketDataClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private TpexMarketDataClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        client = new TpexMarketDataClient(webClient);
    }

    @Test
    @DisplayName("Should parse TPEx master CSV, filter Stage 0 symbols (Active/Leveraged/ETN), and extract authentic listing dates")
    void shouldParseTpexMasterCsvAndExtractAuthenticListingDates() {
        String csv = """
                "出表日期","基金代號","基金簡稱","基金類型","基金中文名稱","基金英文名稱","標的指數/追蹤指數名稱","成立日期","上市日期","發行單位數/轉換數"
                "1150927","00411A","主動統一前沿科技","國內成分證券主動式交易所交易基金(股票)","統一前沿科技主動式ETF","UPAMC Frontier Active ETF","不適用","1150811","1150826","441076000"
                "1150927","00679B","元大美債20年","境外指數股票型基金","元大美債20年ETF","Yuanta U.S. Treasury 20+ Year Bond ETF","ICE美國政府20+年期債券指數","1060111","1060117","6174692000"
                "1150927","00680L","元大美債20正2","境外指數股票型基金","元大美債20正2 ETF","Yuanta 20+ 2X","ICE 2X","1060111","1060117","1000000"
                "1150927","020001","富邦特選ETN","指數投資證券","富邦特選蘋果ETN","Fubon Apple ETN","蘋果指數","1080430","1080430","500000"
                """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(csv));

        StepVerifier.create(client.fetchTpexEtfMasterUniverse())
                .assertNext(asset -> {
                    assertThat(asset.getTicker()).isEqualTo("00679B");
                    assertThat(asset.getName()).isEqualTo("元大美債20年ETF");
                    assertThat(asset.getUnderlyingIndex()).isEqualTo("ICE美國政府20+年期債券指數");
                    // 1060117 -> 2017-01-17
                    assertThat(asset.getListingDate()).isEqualTo(LocalDateTime.of(2017, 1, 17, 0, 0));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fetch TPEx daily quotes and filter out non-ETF securities")
    void shouldFetchTpexDailyQuotesAndFilterNonEtf() throws Exception {
        String json = """
                [
                  {
                    "Date": "1150922",
                    "SecuritiesCompanyCode": "00679B",
                    "CompanyName": "元大美債20年",
                    "Close": "25.58",
                    "Change": "-0.06",
                    "Open": "25.61",
                    "High": "25.62",
                    "Low": "25.57",
                    "TradingShares": "9786000",
                    "TransactionAmount": "250505030",
                    "Capitals": "6180192000"
                  },
                  {
                    "Date": "1150922",
                    "SecuritiesCompanyCode": "5483",
                    "CompanyName": "中美晶",
                    "Close": "135.00",
                    "Change": "1.00",
                    "Open": "134.00",
                    "High": "136.00",
                    "Low": "133.50",
                    "TradingShares": "5000000",
                    "TransactionAmount": "675000000",
                    "Capitals": "5860000000"
                  }
                ]
                """;
        JsonNode node = mapper.readTree(json);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(JsonNode.class)).thenReturn(Flux.fromIterable(node));

        StepVerifier.create(client.fetchTpexDailyQuotes())
                .assertNext(quote -> {
                    assertThat(quote.getTicker()).isEqualTo("00679B");
                    assertThat(quote.getClosePrice()).isEqualByComparingTo(new BigDecimal("25.5800"));
                    assertThat(quote.getVolumeShares()).isEqualTo(9786000L);
                })
                .verifyComplete();
    }
}
