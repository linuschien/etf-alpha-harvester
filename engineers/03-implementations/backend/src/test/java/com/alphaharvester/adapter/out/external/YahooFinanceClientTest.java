package com.alphaharvester.adapter.out.external;

import com.alphaharvester.application.port.out.ExternalMarketDataPort.DividendsAndSplits;
import com.alphaharvester.domain.entity.DividendAnnouncement;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CorporateActionType;
import com.alphaharvester.domain.model.TaxTag;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class YahooFinanceClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final YahooFinanceClient client = new YahooFinanceClient(WebClient.create(), objectMapper);

    @Test
    @DisplayName("Should parse bond ETF dividends with TaxTag.OVERSEAS_76W for ticker ending in 'B'")
    void shouldParseBondDividendWithOverseas76w() throws Exception {
        String json = """
        {
          "chart": {
            "result": [
              {
                "events": {
                  "dividends": {
                    "1700000000": {
                      "amount": 0.35,
                      "date": 1700000000
                    }
                  }
                }
              }
            ]
          }
        }
        """;

        JsonNode root = objectMapper.readTree(json);
        DividendsAndSplits result = client.parseDividendsAndSplits(root, "00679B");

        assertThat(result.dividends()).hasSize(1);
        DividendAnnouncement div = result.dividends().get(0);
        assertThat(div.getTicker()).isEqualTo("00679B");
        assertThat(div.getDividendPerShare().doubleValue()).isEqualTo(0.35);
        assertThat(div.getTaxTag()).isEqualTo(TaxTag.OVERSEAS_76W);
    }

    @Test
    @DisplayName("Should parse equity ETF dividends with TaxTag.DOMESTIC_54C for ticker not ending in 'B'")
    void shouldParseEquityDividendWithDomestic54c() throws Exception {
        String json = """
        {
          "chart": {
            "result": [
              {
                "events": {
                  "dividends": {
                    "1700000000": {
                      "amount": 1.50,
                      "date": 1700000000
                    }
                  }
                }
              }
            ]
          }
        }
        """;

        JsonNode root = objectMapper.readTree(json);
        DividendsAndSplits result = client.parseDividendsAndSplits(root, "0050");

        assertThat(result.dividends()).hasSize(1);
        DividendAnnouncement div = result.dividends().get(0);
        assertThat(div.getTicker()).isEqualTo("0050");
        assertThat(div.getDividendPerShare().doubleValue()).isEqualTo(1.50);
        assertThat(div.getTaxTag()).isEqualTo(TaxTag.DOMESTIC_54C);
    }

    @Test
    @DisplayName("Should parse stock splits correctly")
    void shouldParseStockSplitsCorrectly() throws Exception {
        String json = """
        {
          "chart": {
            "result": [
              {
                "events": {
                  "splits": {
                    "1710000000": {
                      "date": 1710000000,
                      "numerator": 4.0,
                      "denominator": 1.0,
                      "splitRatio": "4:1"
                    }
                  }
                }
              }
            ]
          }
        }
        """;

        JsonNode root = objectMapper.readTree(json);
        DividendsAndSplits result = client.parseDividendsAndSplits(root, "0050");

        assertThat(result.splits()).hasSize(1);
        var split = result.splits().get(0);
        assertThat(split.getTicker()).isEqualTo("0050");
        assertThat(split.getActionType()).isEqualTo(CorporateActionType.SPLIT);
        assertThat(split.getSplitToShares()).isEqualTo(4);
        assertThat(split.getSplitFromShares()).isEqualTo(1);
    }

    private static final String SAMPLE_VALID_CHART_JSON = """
    {
      "chart": {
        "result": [
          {
            "meta": { "exchangeTimezoneName": "Asia/Taipei" },
            "timestamp": [1700000000],
            "indicators": {
              "quote": [
                {
                  "open": [10.0],
                  "high": [10.5],
                  "low": [9.8],
                  "close": [10.2],
                  "volume": [1000]
                }
              ]
            }
          }
        ]
      }
    }
    """;

    @Test
    @DisplayName("Should route bond ETF ending in 'B' directly to .TWO first")
    void shouldRouteBondEtfToTwoFirst() {
        WebClient mockWebClient = org.mockito.Mockito.mock(WebClient.class);
        WebClient.RequestHeadersUriSpec uriSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(WebClient.ResponseSpec.class);

        org.mockito.Mockito.when(mockWebClient.get()).thenReturn(uriSpec);
        org.mockito.Mockito.when(uriSpec.uri(org.mockito.ArgumentMatchers.anyString())).thenReturn(headersSpec);
        org.mockito.Mockito.when(headersSpec.retrieve()).thenReturn(responseSpec);
        org.mockito.Mockito.when(responseSpec.bodyToMono(String.class)).thenReturn(reactor.core.publisher.Mono.just(SAMPLE_VALID_CHART_JSON));

        YahooFinanceClient mockClient = new YahooFinanceClient(mockWebClient, objectMapper);

        reactor.test.StepVerifier.create(mockClient.fetchTaiwanEtfHistoricalQuotes("00720B", "5d"))
                .expectNextCount(1)
                .verifyComplete();

        org.mockito.Mockito.verify(uriSpec).uri(org.mockito.ArgumentMatchers.contains("00720B.TWO?"));
        org.mockito.Mockito.verify(uriSpec, org.mockito.Mockito.never()).uri(org.mockito.ArgumentMatchers.contains("00720B.TW?"));
    }

    @Test
    @DisplayName("Should route equity ETF not ending in 'B' directly to .TW first")
    void shouldRouteEquityEtfToTwFirst() {
        WebClient mockWebClient = org.mockito.Mockito.mock(WebClient.class);
        WebClient.RequestHeadersUriSpec uriSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(WebClient.ResponseSpec.class);

        org.mockito.Mockito.when(mockWebClient.get()).thenReturn(uriSpec);
        org.mockito.Mockito.when(uriSpec.uri(org.mockito.ArgumentMatchers.anyString())).thenReturn(headersSpec);
        org.mockito.Mockito.when(headersSpec.retrieve()).thenReturn(responseSpec);
        org.mockito.Mockito.when(responseSpec.bodyToMono(String.class)).thenReturn(reactor.core.publisher.Mono.just(SAMPLE_VALID_CHART_JSON));

        YahooFinanceClient mockClient = new YahooFinanceClient(mockWebClient, objectMapper);

        reactor.test.StepVerifier.create(mockClient.fetchTaiwanEtfHistoricalQuotes("0050", "5d"))
                .expectNextCount(1)
                .verifyComplete();

        org.mockito.Mockito.verify(uriSpec).uri(org.mockito.ArgumentMatchers.contains("0050.TW?"));
        org.mockito.Mockito.verify(uriSpec, org.mockito.Mockito.never()).uri(org.mockito.ArgumentMatchers.contains("0050.TWO?"));
    }

    @Test
    @DisplayName("Should fallback to .TW when bond ETF .TWO is empty")
    void shouldFallbackWhenBondPrimaryIsEmpty() {
        WebClient mockWebClient = org.mockito.Mockito.mock(WebClient.class);
        WebClient.RequestHeadersUriSpec uriSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(WebClient.ResponseSpec.class);

        org.mockito.Mockito.when(mockWebClient.get()).thenReturn(uriSpec);
        org.mockito.Mockito.when(uriSpec.uri(org.mockito.ArgumentMatchers.anyString())).thenReturn(headersSpec);
        org.mockito.Mockito.when(headersSpec.retrieve()).thenReturn(responseSpec);
        org.mockito.Mockito.when(responseSpec.bodyToMono(String.class)).thenReturn(reactor.core.publisher.Mono.just("{}"));

        YahooFinanceClient mockClient = new YahooFinanceClient(mockWebClient, objectMapper);

        reactor.test.StepVerifier.create(mockClient.fetchTaiwanEtfHistoricalQuotes("00720B", "5d"))
                .verifyComplete();

        org.mockito.Mockito.verify(uriSpec).uri(org.mockito.ArgumentMatchers.contains("00720B.TWO?"));
        org.mockito.Mockito.verify(uriSpec).uri(org.mockito.ArgumentMatchers.contains("00720B.TW?"));
    }

    @Test
    @DisplayName("Should handle 404 NotFound gracefully without throwing exception")
    void shouldHandle404Gracefully() {
        WebClient mockWebClient = org.mockito.Mockito.mock(WebClient.class);
        WebClient.RequestHeadersUriSpec uriSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(WebClient.ResponseSpec.class);

        org.mockito.Mockito.when(mockWebClient.get()).thenReturn(uriSpec);
        org.mockito.Mockito.when(uriSpec.uri(org.mockito.ArgumentMatchers.anyString())).thenReturn(headersSpec);
        org.mockito.Mockito.when(headersSpec.retrieve()).thenReturn(responseSpec);
        org.mockito.Mockito.when(responseSpec.bodyToMono(String.class))
                .thenReturn(reactor.core.publisher.Mono.error(
                        org.springframework.web.reactive.function.client.WebClientResponseException.create(404, "Not Found", null, null, null)));

        YahooFinanceClient mockClient = new YahooFinanceClient(mockWebClient, objectMapper);

        reactor.test.StepVerifier.create(mockClient.fetchHistoricalQuotes("UNKNOWN.TW", "5d"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should NOT trigger fallback when primary dividends query returns 200 OK with no events")
    void shouldNotTriggerFallbackWhenPrimaryReturnsEmptyEvents() {
        WebClient mockWebClient = org.mockito.Mockito.mock(WebClient.class);
        WebClient.RequestHeadersUriSpec uriSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(WebClient.ResponseSpec.class);

        org.mockito.Mockito.when(mockWebClient.get()).thenReturn(uriSpec);
        org.mockito.Mockito.when(uriSpec.uri(org.mockito.ArgumentMatchers.anyString())).thenReturn(headersSpec);
        org.mockito.Mockito.when(headersSpec.retrieve()).thenReturn(responseSpec);
        // Valid JSON with empty events
        org.mockito.Mockito.when(responseSpec.bodyToMono(String.class)).thenReturn(reactor.core.publisher.Mono.just("{\"chart\":{\"result\":[{\"events\":{}}]}}"));

        YahooFinanceClient mockClient = new YahooFinanceClient(mockWebClient, objectMapper);

        reactor.test.StepVerifier.create(mockClient.fetchDividendsAndSplits("0050", "1mo"))
                .assertNext(res -> {
                    assertThat(res.dividends()).isEmpty();
                    assertThat(res.splits()).isEmpty();
                })
                .verifyComplete();

        // Should call 0050.TW once
        org.mockito.Mockito.verify(uriSpec, org.mockito.Mockito.times(1)).uri(org.mockito.ArgumentMatchers.contains("0050.TW?"));
        // Should NEVER call 0050.TWO
        org.mockito.Mockito.verify(uriSpec, org.mockito.Mockito.never()).uri(org.mockito.ArgumentMatchers.contains("0050.TWO?"));
    }

    @Test
    @DisplayName("Should trigger fallback when primary dividends query returns 404 NotFound")
    void shouldTriggerFallbackWhenPrimaryReturns404() {
        WebClient mockWebClient = org.mockito.Mockito.mock(WebClient.class);
        WebClient.RequestHeadersUriSpec uriSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(WebClient.ResponseSpec.class);

        org.mockito.Mockito.when(mockWebClient.get()).thenReturn(uriSpec);
        org.mockito.Mockito.when(uriSpec.uri(org.mockito.ArgumentMatchers.anyString())).thenReturn(headersSpec);
        org.mockito.Mockito.when(headersSpec.retrieve()).thenReturn(responseSpec);
        org.mockito.Mockito.when(responseSpec.bodyToMono(String.class))
                .thenReturn(reactor.core.publisher.Mono.error(
                        org.springframework.web.reactive.function.client.WebClientResponseException.create(404, "Not Found", null, null, null)));

        YahooFinanceClient mockClient = new YahooFinanceClient(mockWebClient, objectMapper);

        reactor.test.StepVerifier.create(mockClient.fetchDividendsAndSplits("00720B", "1mo"))
                .assertNext(res -> {
                    assertThat(res.dividends()).isEmpty();
                    assertThat(res.splits()).isEmpty();
                })
                .verifyComplete();

        // 00720B is a bond ETF, so primary is .TWO, fallback is .TW
        org.mockito.Mockito.verify(uriSpec).uri(org.mockito.ArgumentMatchers.contains("00720B.TWO?"));
        org.mockito.Mockito.verify(uriSpec).uri(org.mockito.ArgumentMatchers.contains("00720B.TW?"));
    }

    @Test
    @DisplayName("Should recover missing close price from meta.regularMarketPrice when quote.close is null and regularMarketDate matches candle date")
    void shouldRecoverCloseFromMetaWhenCloseIsNullAndDateMatches() {
        // 1792371600 = 2026-10-19 09:00:00 CST (+08:00)
        // 1792388005 = 2026-10-19 13:33:25 CST (+08:00)
        String json = """
        {
          "chart": {
            "result": [
              {
                "meta": {
                  "exchangeTimezoneName": "Asia/Taipei",
                  "regularMarketTime": 1792388005,
                  "regularMarketPrice": 46822.08,
                  "regularMarketDayHigh": 46844.64,
                  "regularMarketDayLow": 46170.0,
                  "regularMarketVolume": 5000000
                },
                "timestamp": [1792371600],
                "indicators": {
                  "quote": [
                    {
                      "open": [46844.64],
                      "high": [46844.64],
                      "low": [46170.0],
                      "close": [null],
                      "volume": [0]
                    }
                  ]
                }
              }
            ]
          }
        }
        """;

        WebClient mockWebClient = org.mockito.Mockito.mock(WebClient.class);
        WebClient.RequestHeadersUriSpec uriSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(WebClient.ResponseSpec.class);

        org.mockito.Mockito.when(mockWebClient.get()).thenReturn(uriSpec);
        org.mockito.Mockito.when(uriSpec.uri(org.mockito.ArgumentMatchers.anyString())).thenReturn(headersSpec);
        org.mockito.Mockito.when(headersSpec.retrieve()).thenReturn(responseSpec);
        org.mockito.Mockito.when(responseSpec.bodyToMono(String.class)).thenReturn(reactor.core.publisher.Mono.just(json));

        YahooFinanceClient mockClient = new YahooFinanceClient(mockWebClient, objectMapper);

        reactor.test.StepVerifier.create(mockClient.fetchHistoricalQuotes("^TWII", "5d"))
                .assertNext(q -> {
                    assertThat(q.getTicker()).isEqualTo("^TWII");
                    assertThat(q.getTradeDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 19).atStartOfDay());
                    assertThat(q.getClosePrice()).isEqualTo(new java.math.BigDecimal("46822.0800"));
                    assertThat(q.getOpenPrice()).isEqualTo(new java.math.BigDecimal("46844.6400"));
                    assertThat(q.getHighPrice()).isEqualTo(new java.math.BigDecimal("46844.6400"));
                    assertThat(q.getLowPrice()).isEqualTo(new java.math.BigDecimal("46170.0000"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should NOT recover close price from meta if regularMarketDate does NOT match candle date (e.g. ^VXN lagged scenario)")
    void shouldNotRecoverCloseWhenRegularMarketDateDiffersFromCandleDate() {
        // 1792416600 = 2026-10-19 09:30:00 EDT (-04:00) (Monday)
        // 1792180800 = 2026-10-16 16:00:00 EDT (-04:00) (Friday - stale date)
        String json = """
        {
          "chart": {
            "result": [
              {
                "meta": {
                  "exchangeTimezoneName": "America/New_York",
                  "regularMarketTime": 1792180800,
                  "regularMarketPrice": 27.13
                },
                "timestamp": [1792416600],
                "indicators": {
                  "quote": [
                    {
                      "open": [null],
                      "high": [null],
                      "low": [null],
                      "close": [null],
                      "volume": [null]
                    }
                  ]
                }
              }
            ]
          }
        }
        """;

        WebClient mockWebClient = org.mockito.Mockito.mock(WebClient.class);
        WebClient.RequestHeadersUriSpec uriSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = org.mockito.Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = org.mockito.Mockito.mock(WebClient.ResponseSpec.class);

        org.mockito.Mockito.when(mockWebClient.get()).thenReturn(uriSpec);
        org.mockito.Mockito.when(uriSpec.uri(org.mockito.ArgumentMatchers.anyString())).thenReturn(headersSpec);
        org.mockito.Mockito.when(headersSpec.retrieve()).thenReturn(responseSpec);
        org.mockito.Mockito.when(responseSpec.bodyToMono(String.class)).thenReturn(reactor.core.publisher.Mono.just(json));

        YahooFinanceClient mockClient = new YahooFinanceClient(mockWebClient, objectMapper);

        // Monday candle with null close and stale Friday regularMarketTime must be dropped
        reactor.test.StepVerifier.create(mockClient.fetchHistoricalQuotes("^VXN", "5d"))
                .verifyComplete();
    }
}
