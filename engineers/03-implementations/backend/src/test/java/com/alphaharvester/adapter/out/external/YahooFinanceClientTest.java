package com.alphaharvester.adapter.out.external;

import com.alphaharvester.application.port.out.ExternalMarketDataPort.DividendsAndSplits;
import com.alphaharvester.domain.entity.DividendAnnouncement;
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
}
