package com.alphaharvester.adapter.out.external;

import com.alphaharvester.domain.entity.CorporateAction;
import com.alphaharvester.domain.entity.DividendAnnouncement;
import com.alphaharvester.domain.entity.MacroYieldSnapshot;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CorporateActionType;
import com.alphaharvester.domain.model.TaxTag;
import com.alphaharvester.application.port.out.ExternalMarketDataPort.DividendsAndSplits;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
    private static final String DEFAULT_RECENT_RANGE = "5d";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public YahooFinanceClient(WebClient webClient, @Autowired(required = false) ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Fetches historical daily quotes for any symbol (index or ETF) from Yahoo Finance Chart API.
     * E.g. range = "1mo", "3mo", "1y", "2y", "5d".
     */
    public Flux<MarketDailyQuote> fetchHistoricalQuotes(String symbol, String range) {
        String url = YAHOO_CHART_BASE + symbol + "?interval=1d&range=" + range;
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                        .maxBackoff(Duration.ofSeconds(3))
                        .filter(t -> t instanceof WebClientResponseException e &&
                                (e.getStatusCode().value() == 429 || e.getStatusCode().is5xxServerError())))
                .flatMapMany(jsonStr -> {
                    try {
                        JsonNode root = objectMapper.readTree(jsonStr);
                        JsonNode result = root.path("chart").path("result").get(0);
                        if (result == null) return Flux.empty();

                        String tzName = result.path("meta").path("exchangeTimezoneName").asText("");
                        ZoneId zoneId;
                        try {
                            zoneId = !tzName.isBlank() ? ZoneId.of(tzName) : ZoneId.systemDefault();
                        } catch (Exception ex) {
                            zoneId = ZoneId.systemDefault();
                        }

                        JsonNode timestamps = result.path("timestamp");
                        JsonNode quote = result.path("indicators").path("quote").get(0);
                        if (timestamps == null || quote == null || !timestamps.isArray() || timestamps.isEmpty()) {
                            return Flux.empty();
                        }

                        List<MarketDailyQuote> list = new ArrayList<>();
                        for (int i = 0; i < timestamps.size(); i++) {
                            long epochSec = timestamps.get(i).asLong();
                            LocalDateTime tradeDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSec), zoneId).toLocalDate().atStartOfDay();

                            BigDecimal open = parseBigDecimalSafe(quote.path("open").get(i));
                            BigDecimal high = parseBigDecimalSafe(quote.path("high").get(i));
                            BigDecimal low = parseBigDecimalSafe(quote.path("low").get(i));
                            BigDecimal close = parseBigDecimalSafe(quote.path("close").get(i));
                            long volume = quote.path("volume").get(i).asLong(0L);

                            if (close != null) {
                                BigDecimal tradeValue = close.multiply(BigDecimal.valueOf(volume)).setScale(2, RoundingMode.HALF_UP);
                                list.add(new MarketDailyQuote(
                                        null, null, null, symbol, tradeDate,
                                        open, high, low, close, volume, tradeValue, null, null
                                ));
                            }
                        }
                        return Flux.fromIterable(list);
                    } catch (Exception e) {
                        log.error("Failed to parse Yahoo historical chart quotes for symbol '{}': {}", symbol, e.getMessage());
                        return Flux.empty();
                    }

                })
                .onErrorResume(e -> {
                    log.error("Error fetching Yahoo historical quotes for '{}': {}", symbol, e.getMessage(), e);
                    return Flux.empty();
                });
    }

    /**
     * Fallback to fetch Taiwan ETF quote from Yahoo Finance when TWSE/TPEx misses data.
     * Tries {ticker}.TW (TWSE listed) first, then {ticker}.TWO (TPEx OTC listed).
     */
    public Mono<MarketDailyQuote> fetchTaiwanEtfQuote(String ticker) {
        log.info("Attempting Yahoo Finance fallback quote fetch for ETF '{}'...", ticker);
        return fetchTaiwanEtfHistoricalQuotes(ticker, DEFAULT_RECENT_RANGE).last()
                .onErrorResume(e -> Mono.empty())
                .doOnSuccess(q -> {
                    if (q != null) {
                        log.info("Successfully recovered ETF '{}' quote from Yahoo Finance fallback (close: {})", ticker, q.getClosePrice());
                    }
                });
    }

    /**
     * Multi-day historical backfill for Taiwan ETF from Yahoo Finance.
     * Tries {ticker}.TW first, then {ticker}.TWO.
     */
    public Flux<MarketDailyQuote> fetchTaiwanEtfHistoricalQuotes(String ticker, String range) {
        log.info("Attempting Yahoo Finance historical backfill for ETF '{}' (range: {})...", ticker, range);
        return fetchHistoricalQuotes(ticker + ".TW", range)
                .switchIfEmpty(Flux.defer(() -> fetchHistoricalQuotes(ticker + ".TWO", range)))
                .map(q -> {
                    q.setTicker(ticker);
                    return q;
                });
    }


    /**
     * Concurrently fetches dividend announcements and stock split corporate actions
     * in a single external request from Yahoo Finance chart events with specified range.
     */
    public Mono<DividendsAndSplits> fetchDividendsAndSplits(String ticker, String range) {
        if (ticker == null || ticker.isBlank()) {
            return Mono.just(new DividendsAndSplits(List.of(), List.of()));
        }

        String effectiveRange = (range != null && !range.isBlank()) ? range : "1y";

        if (ticker.contains(".")) {
            return fetchDirectDividendsAndSplits(ticker, ticker, effectiveRange);
        }

        String primarySuffix = ticker.endsWith("B") ? ".TWO" : ".TW";
        String fallbackSuffix = ticker.endsWith("B") ? ".TW" : ".TWO";

        return fetchDirectDividendsAndSplits(ticker + primarySuffix, ticker, effectiveRange)
                .flatMap(res -> {
                    if (res.dividends().isEmpty() && res.splits().isEmpty()) {
                        return fetchDirectDividendsAndSplits(ticker + fallbackSuffix, ticker, effectiveRange);
                    }
                    return Mono.just(res);
                });
    }

    private Mono<DividendsAndSplits> fetchDirectDividendsAndSplits(String symbol, String ticker, String range) {
        String url = YAHOO_CHART_BASE + symbol + "?interval=1d&range=" + range + "&events=div,split";
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .map(jsonStr -> {
                    try {
                        return parseDividendsAndSplits(objectMapper.readTree(jsonStr), ticker);
                    } catch (Exception e) {
                        log.debug("Error parsing Yahoo dividends & splits for '{}': {}", symbol, e.getMessage());
                        return new DividendsAndSplits(List.of(), List.of());
                    }
                })
                .onErrorResume(e -> {
                    log.debug("Error fetching Yahoo dividends & splits for '{}': {}", symbol, e.getMessage());
                    return Mono.just(new DividendsAndSplits(List.of(), List.of()));
                });
    }

    DividendsAndSplits parseDividendsAndSplits(JsonNode root, String ticker) {
        try {
            JsonNode result = root.path("chart").path("result").get(0);
            if (result == null) return new DividendsAndSplits(List.of(), List.of());

            JsonNode eventsNode = result.path("events");
            if (eventsNode.isMissingNode() || !eventsNode.isObject()) {
                return new DividendsAndSplits(List.of(), List.of());
            }

            List<DividendAnnouncement> dividends = new ArrayList<>();
            JsonNode divNode = eventsNode.path("dividends");
            if (!divNode.isMissingNode() && divNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = divNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode item = entry.getValue();
                    long epoch = item.path("date").asLong();
                    LocalDateTime exDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.of("Asia/Taipei")).toLocalDate().atStartOfDay();
                    BigDecimal amount = BigDecimal.valueOf(item.path("amount").asDouble(0.0)).setScale(4, RoundingMode.HALF_UP);
                    TaxTag taxTag = (ticker != null && ticker.endsWith("B"))
                            ? TaxTag.OVERSEAS_76W
                            : TaxTag.DOMESTIC_54C;

                    dividends.add(new DividendAnnouncement(
                            null, null, ticker, exDate, exDate.plusDays(30),
                            amount, taxTag
                    ));
                }
            }

            List<CorporateAction> actions = new ArrayList<>();
            JsonNode splitsNode = eventsNode.path("splits");
            if (!splitsNode.isMissingNode() && splitsNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = splitsNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode item = entry.getValue();
                    long epoch = item.path("date").asLong();
                    LocalDateTime effectiveDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.of("Asia/Taipei")).toLocalDate().atStartOfDay();
                    int toShares = (int) Math.round(item.path("numerator").asDouble(1.0));
                    int fromShares = (int) Math.round(item.path("denominator").asDouble(1.0));

                    actions.add(new CorporateAction(
                            null, null, ticker, CorporateActionType.SPLIT,
                            effectiveDate, toShares, fromShares
                    ));
                }
            }

            return new DividendsAndSplits(dividends, actions);
        } catch (Exception e) {
            log.error("Error parsing Yahoo dividends and splits for '{}': {}", ticker, e.getMessage());
            return new DividendsAndSplits(List.of(), List.of());
        }
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
