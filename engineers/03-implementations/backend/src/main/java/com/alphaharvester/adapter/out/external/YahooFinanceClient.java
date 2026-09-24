package com.alphaharvester.adapter.out.external;

import com.alphaharvester.domain.entity.CorporateAction;
import com.alphaharvester.domain.entity.DividendAnnouncement;
import com.alphaharvester.domain.entity.MacroYieldSnapshot;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CorporateActionType;
import com.alphaharvester.domain.model.TaxTag;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class YahooFinanceClient {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceClient.class);

    private static final String YAHOO_CHART_BASE = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private final WebClient webClient;

    public YahooFinanceClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetches daily quote for a global benchmark index (e.g. ^TWII, ^GSPC, ^NDX, ^SOX, ^N225, ^VIX, ^VXN, ^MOVE).
     */
    public Mono<MarketDailyQuote> fetchBenchmarkQuote(String symbol) {
        String url = YAHOO_CHART_BASE + symbol + "?interval=1d&range=5d";
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(root -> {
                    try {
                        JsonNode result = root.path("chart").path("result").get(0);
                        if (result == null) return Mono.empty();

                        JsonNode timestamps = result.path("timestamp");
                        JsonNode quote = result.path("indicators").path("quote").get(0);
                        if (timestamps == null || quote == null || !timestamps.isArray() || timestamps.isEmpty()) {
                            return Mono.empty();
                        }

                        int lastIdx = timestamps.size() - 1;
                        long epochSec = timestamps.get(lastIdx).asLong();
                        LocalDateTime tradeDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSec), ZoneId.systemDefault());

                        BigDecimal open = parseBigDecimalSafe(quote.path("open").get(lastIdx));
                        BigDecimal high = parseBigDecimalSafe(quote.path("high").get(lastIdx));
                        BigDecimal low = parseBigDecimalSafe(quote.path("low").get(lastIdx));
                        BigDecimal close = parseBigDecimalSafe(quote.path("close").get(lastIdx));
                        long volume = quote.path("volume").get(lastIdx).asLong(0L);

                        if (close == null) return Mono.empty();

                        MarketDailyQuote mdq = new MarketDailyQuote(
                                null, null, null, symbol, tradeDate,
                                open, high, low, close, volume, BigDecimal.ZERO, null, null
                        );
                        return Mono.just(mdq);
                    } catch (Exception e) {
                        log.error("Failed to parse Yahoo chart quote for symbol '{}': {}", symbol, e.getMessage());
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error fetching Yahoo chart quote for '{}': {}", symbol, e.getMessage(), e);
                    return Mono.empty();
                });
    }

    /**
     * Fetches Treasury yields from Yahoo Finance (^TNX 10Y, ^TYX 30Y) with Zero API key.
     */
    public Mono<MacroYieldSnapshot> fetchMacroYields() {
        LocalDateTime now = LocalDateTime.now();
        return fetchBenchmarkQuote("^TNX")
                .flatMap(tnx -> fetchBenchmarkQuote("^TYX")
                        .map(tyx -> {
                            BigDecimal y10 = tnx.getClosePrice();
                            BigDecimal y30 = tyx.getClosePrice();
                            BigDecimal y20 = y10.add(y30).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
                            BigDecimal corpYield = y20.add(new BigDecimal("1.2500"));
                            BigDecimal spread = y10.subtract(new BigDecimal("4.0000")); // estimated 10Y-2Y spread

                            return new MacroYieldSnapshot(
                                    null, now, corpYield, y10, y20, spread
                            );
                        }))
                .onErrorResume(e -> {
                    log.error("Error fetching macro yields from Yahoo Finance: {}", e.getMessage(), e);
                    return Mono.empty();
                });
    }

    /**
     * Fetches historical splits for a ticker from Yahoo Finance events.
     */
    public Flux<CorporateAction> fetchSplits(String ticker) {
        String url = YAHOO_CHART_BASE + ticker + "?interval=1d&range=1y&events=div,split";
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .<CorporateAction>flatMapMany(root -> {
                    try {
                        JsonNode result = root.path("chart").path("result").get(0);
                        if (result == null) return Flux.<CorporateAction>empty();
                        JsonNode splitsNode = result.path("events").path("splits");
                        if (splitsNode.isMissingNode() || !splitsNode.isObject()) {
                            return Flux.<CorporateAction>empty();
                        }

                        List<CorporateAction> actions = new ArrayList<>();
                        Iterator<Map.Entry<String, JsonNode>> fields = splitsNode.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fields.next();
                            JsonNode item = entry.getValue();
                            long epoch = item.path("date").asLong();
                            LocalDateTime effectiveDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault());
                            int toShares = (int) Math.round(item.path("numerator").asDouble(1.0));
                            int fromShares = (int) Math.round(item.path("denominator").asDouble(1.0));

                            actions.add(new CorporateAction(
                                    null, null, ticker, CorporateActionType.SPLIT,
                                    effectiveDate, toShares, fromShares
                            ));
                        }
                        return Flux.fromIterable(actions);
                    } catch (Exception e) {
                        log.error("Error parsing Yahoo splits for '{}': {}", ticker, e.getMessage());
                        return Flux.<CorporateAction>empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error fetching Yahoo splits for '{}': {}", ticker, e.getMessage(), e);
                    return Flux.empty();
                });
    }

    /**
     * Fetches historical dividend announcements for a ticker from Yahoo Finance events.
     */
    public Flux<DividendAnnouncement> fetchDividends(String ticker) {
        String url = YAHOO_CHART_BASE + ticker + "?interval=1d&range=1y&events=div,split";
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .<DividendAnnouncement>flatMapMany(root -> {
                    try {
                        JsonNode result = root.path("chart").path("result").get(0);
                        if (result == null) return Flux.<DividendAnnouncement>empty();
                        JsonNode divNode = result.path("events").path("dividends");
                        if (divNode.isMissingNode() || !divNode.isObject()) {
                            return Flux.<DividendAnnouncement>empty();
                        }

                        List<DividendAnnouncement> dividends = new ArrayList<>();
                        Iterator<Map.Entry<String, JsonNode>> fields = divNode.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fields.next();
                            JsonNode item = entry.getValue();
                            long epoch = item.path("date").asLong();
                            LocalDateTime exDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault());
                            BigDecimal amount = BigDecimal.valueOf(item.path("amount").asDouble(0.0)).setScale(4, RoundingMode.HALF_UP);

                            dividends.add(new DividendAnnouncement(
                                    null, null, ticker, exDate, exDate.plusDays(30),
                                    amount, TaxTag.DOMESTIC_54C
                            ));
                        }
                        return Flux.fromIterable(dividends);
                    } catch (Exception e) {
                        log.error("Error parsing Yahoo dividends for '{}': {}", ticker, e.getMessage());
                        return Flux.<DividendAnnouncement>empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error fetching Yahoo dividends for '{}': {}", ticker, e.getMessage(), e);
                    return Flux.empty();
                });
    }

    private BigDecimal parseBigDecimalSafe(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        try {
            return BigDecimal.valueOf(node.asDouble()).setScale(4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }
}
