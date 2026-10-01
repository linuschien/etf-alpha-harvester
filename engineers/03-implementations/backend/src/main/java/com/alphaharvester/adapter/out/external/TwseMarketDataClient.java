package com.alphaharvester.adapter.out.external;

import com.alphaharvester.adapter.out.external.util.RocDateUtil;
import com.alphaharvester.domain.entity.DcaPopularityRank;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.entity.MarketDailyQuote;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class TwseMarketDataClient {

    private static final Logger log = LoggerFactory.getLogger(TwseMarketDataClient.class);

    private static final Retry RETRY_SPEC = Retry.backoff(3, Duration.ofSeconds(1))
            .maxBackoff(Duration.ofSeconds(4))
            .filter(t -> !(t instanceof WebClientResponseException e && e.getStatusCode().is4xxClientError() && e.getStatusCode().value() != 429));

    /**
     * Stage 0 positive allowlist pattern:
     * Only accepts pure digit tickers (00\d{2,4}) or bond ETFs ending with 'B' (00\d{2,4}B).
     * Automatically filters out leveraged (L), inverse (R), futures (U), active (A/D),
     * balanced (T), foreign currency counters (K/C), and ETNs (02...).
     */
    public static final Pattern STAGE_0_ALLOWLIST_PATTERN = Pattern.compile("^00\\d{2,4}B?$");

    private static final String TWSE_MASTER_URL = "https://openapi.twse.com.tw/v1/opendata/t187ap47_L";
    private static final String TWSE_QUOTES_URL = "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL";
    private static final String TWSE_MIS_NAV_URL = "https://mis.twse.com.tw/stock/data/all_etf.txt";
    private static final String TWSE_DCA_URL = "https://openapi.twse.com.tw/v1/ETFReport/ETFRank";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public TwseMarketDataClient(WebClient webClient, @Autowired(required = false) ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Fetches all TWSE listed ETF metadata from TWSE OpenAPI.
     * Enforces Stage 0 regex short-circuit blocking: filters out U, L, R, A, and 02 ETNs.
     * Listing dates are parsed directly from authentic TWSE records.
     * Fund size (AUM) is nullable and evaluated dynamically during monthly Top List calculation.
     */
    public Flux<GlobalAssetMetadata> fetchEtfMasterUniverse() {
        LocalDateTime now = LocalDateTime.now();
        return fetchJsonArray(TWSE_MASTER_URL)
                .map(node -> {
                    String ticker = node.path("基金代號").asText("").trim();
                    String shortName = node.path("基金簡稱").asText("").trim();
                    String fullName = node.path("基金中文名稱").asText(shortName).trim();
                    String underlyingIndex = node.path("標的指數/追蹤指數名稱").asText("").trim();
                    String listingDateStr = node.path("上市日期").asText("");
                    LocalDateTime listingDate = RocDateUtil.parseRocDate(listingDateStr);

                    return new GlobalAssetMetadata(
                            null, ticker, fullName, listingDate, underlyingIndex,
                            null, now, now, null
                    );
                })
                .filter(asset -> !asset.getTicker().isBlank() && STAGE_0_ALLOWLIST_PATTERN.matcher(asset.getTicker()).matches())
                .doOnError(e -> log.error("Failed to fetch TWSE ETF master universe: {}", e.getMessage(), e));
    }

    /**
     * Fetches daily trading quotes from TWSE concentrated market.
     * Enforces Stage 0 filter to block leveraged, inverse, futures, active, and ETN symbols.
     */
    public Flux<MarketDailyQuote> fetchTwseDailyQuotes() {
        LocalDateTime now = LocalDateTime.now().toLocalDate().atStartOfDay();
        return fetchJsonArray(TWSE_QUOTES_URL)
                .filter(node -> STAGE_0_ALLOWLIST_PATTERN.matcher(node.path("Code").asText("").trim()).matches())
                .map(node -> {
                    String ticker = node.path("Code").asText("").trim();
                    String dateStr = node.path("Date").asText("");
                    LocalDateTime tradeDate = RocDateUtil.parseRocDate(dateStr, now);
                    if (tradeDate != null) {
                        tradeDate = tradeDate.toLocalDate().atStartOfDay();
                    } else {
                        tradeDate = now;
                    }
                    BigDecimal openPrice = parseBigDecimalSafe(node.path("OpeningPrice").asText(""));
                    BigDecimal highPrice = parseBigDecimalSafe(node.path("HighestPrice").asText(""));
                    BigDecimal lowPrice = parseBigDecimalSafe(node.path("LowestPrice").asText(""));
                    BigDecimal closePrice = parseBigDecimalSafe(node.path("ClosingPrice").asText(""));
                    long volume = parseLongSafe(node.path("TradeVolume").asText("0"));
                    BigDecimal tradeValue = parseBigDecimalSafe(node.path("TradeValue").asText("0"));

                    return new MarketDailyQuote(
                            null, null, null, ticker, tradeDate,
                            openPrice, highPrice, lowPrice, closePrice,
                            volume, tradeValue, null, null
                    );
                })
                .filter(q -> q.getClosePrice() != null)
                .doOnError(e -> log.error("Failed to fetch TWSE daily quotes: {}", e.getMessage(), e));
    }

    /**
     * Fetches real-time / closing NAV & discount/premium percentage from TWSE MIS.
     */
    public Mono<Map<String, NavSnapshot>> fetchMisNavData() {
        return webClient.get()
                .uri(TWSE_MIS_NAV_URL)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(RETRY_SPEC)
                .map(json -> {
                    Map<String, NavSnapshot> map = new HashMap<>();
                    try {
                        JsonNode root = objectMapper.readTree(json);
                        JsonNode groups = root.path("a1");
                        if (groups.isArray()) {
                            for (JsonNode group : groups) {
                                JsonNode msgArray = group.path("msgArray");
                                if (msgArray.isArray()) {
                                    for (JsonNode item : msgArray) {
                                        String symbol = item.path("a").asText("").trim();
                                        String name = item.path("b").asText("").trim();
                                        if (symbol.isBlank()) continue;
                                        BigDecimal nav = parseBigDecimalSafe(item.path("f").asText(""));
                                        BigDecimal discountPrem = parseBigDecimalSafe(item.path("g").asText(""));
                                        long shares = parseLongSafe(item.path("c").asText("0").split("\\.")[0]);
                                        map.put(symbol, new NavSnapshot(name, nav, discountPrem, shares));
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse TWSE MIS NAV: {}", e.getMessage());
                    }
                    return map;
                })
                .doOnError(e -> log.error("Failed to fetch TWSE MIS NAV: {}", e.getMessage(), e));
    }

    /**
     * Fetches regular quota (DCA) Top 20 ETF rankings from TWSE OpenAPI.
     */
    public Flux<DcaPopularityRank> fetchDcaRankings(int year, int month) {
        return fetchJsonArray(TWSE_DCA_URL)
                .map(node -> {
                    int rank = node.path("No").asInt(0);
                    String etfSymbol = node.path("ETFsSecurityCode").asText("").trim();
                    int accounts = (int) parseLongSafe(node.path("ETFsNumberofTradingAccounts").asText("0"));

                    return new DcaPopularityRank(
                            null, null, etfSymbol, year, month, rank, accounts
                    );
                })
                .filter(r -> !r.getTicker().isBlank() && r.getRankPosition() > 0)
                .doOnError(e -> log.error("Failed to fetch TWSE DCA rankings: {}", e.getMessage(), e));
    }

    private Flux<JsonNode> fetchJsonArray(String url) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(RETRY_SPEC)
                .flatMapMany(json -> {
                    try {
                        JsonNode root = objectMapper.readTree(json);
                        return (root != null && root.isArray()) ? Flux.fromIterable(root) : Flux.empty();
                    } catch (Exception e) {
                        return Flux.error(e);
                    }
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

    public record NavSnapshot(String name, BigDecimal nav, BigDecimal discountPremiumPct, long sharesOutstanding) {}
}
