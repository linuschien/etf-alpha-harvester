package com.alphaharvester.adapter.out.external;

import com.alphaharvester.adapter.out.external.util.RocDateUtil;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Component
public class TpexMarketDataClient {

    private static final Logger log = LoggerFactory.getLogger(TpexMarketDataClient.class);

    private static final String TPEX_QUOTES_URL = "https://www.tpex.org.tw/openapi/v1/tpex_mainboard_quotes";

    private final WebClient webClient;

    public TpexMarketDataClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetches daily trading quotes from TPEx OTC market (Bond and OTC ETFs).
     */
    public Flux<MarketDailyQuote> fetchTpexDailyQuotes() {
        LocalDateTime now = LocalDateTime.now();
        return webClient.get()
                .uri(TPEX_QUOTES_URL)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .filter(node -> {
                    String code = node.path("SecuritiesCompanyCode").asText("").trim();
                    return code.startsWith("00"); // filter ETFs
                })
                .map(node -> {
                    String ticker = node.path("SecuritiesCompanyCode").asText("").trim();
                    String dateStr = node.path("Date").asText("");
                    LocalDateTime tradeDate = RocDateUtil.parseRocDate(dateStr, now);

                    BigDecimal openPrice = parseBigDecimalSafe(node.path("Open").asText(""));
                    BigDecimal highPrice = parseBigDecimalSafe(node.path("High").asText(""));
                    BigDecimal lowPrice = parseBigDecimalSafe(node.path("Low").asText(""));
                    BigDecimal closePrice = parseBigDecimalSafe(node.path("Close").asText(""));
                    long volume = parseLongSafe(node.path("TradingShares").asText("0"));
                    BigDecimal tradeValue = parseBigDecimalSafe(node.path("TransactionAmount").asText("0"));

                    return new MarketDailyQuote(
                            null, null, null, ticker, tradeDate,
                            openPrice, highPrice, lowPrice, closePrice,
                            volume, tradeValue, null, null
                    );
                })
                .filter(q -> q.getClosePrice() != null)
                .onErrorResume(e -> {
                    log.error("Failed to fetch TPEx OTC daily quotes: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }

    private long parseLongSafe(String str) {
        if (str == null) return 0L;
        String clean = str.replace(",", "").trim();
        try {
            return Long.parseLong(clean);
        } catch (Exception e) {
            return 0L;
        }
    }

    private BigDecimal parseBigDecimalSafe(String str) {
        if (str == null || str.isBlank() || str.equals("--")) return null;
        String clean = str.replace(",", "").trim();
        try {
            return new BigDecimal(clean).setScale(4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }
}

