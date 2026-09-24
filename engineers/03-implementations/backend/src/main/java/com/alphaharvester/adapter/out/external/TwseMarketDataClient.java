package com.alphaharvester.adapter.out.external;

import com.alphaharvester.adapter.out.external.util.RocDateUtil;
import com.alphaharvester.domain.entity.DcaPopularityRank;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class TwseMarketDataClient {

    private static final Logger log = LoggerFactory.getLogger(TwseMarketDataClient.class);

    private static final String TWSE_MASTER_URL = "https://openapi.twse.com.tw/v1/opendata/t187ap47_L";
    private static final String TWSE_QUOTES_URL = "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL";
    private static final String TWSE_MIS_NAV_URL = "https://mis.twse.com.tw/stock/data/all_etf.txt";
    private static final String TWSE_DCA_URL = "https://openapi.twse.com.tw/v1/ETFReport/ETFRank";

    private final WebClient webClient;

    public TwseMarketDataClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetches all listed ETF metadata from TWSE OpenAPI.
     */
    public Flux<GlobalAssetMetadata> fetchEtfMasterUniverse() {
        LocalDateTime now = LocalDateTime.now();
        return fetchMisNavData()
                .defaultIfEmpty(Map.of())
                .flatMapMany(navMap -> webClient.get()
                        .uri(TWSE_MASTER_URL)
                        .retrieve()
                        .bodyToFlux(JsonNode.class)
                        .map(node -> {
                            String ticker = node.path("基金代號").asText("").trim();
                            String shortName = node.path("基金簡稱").asText("").trim();
                            String fullName = node.path("基金中文名稱").asText(shortName).trim();
                            String underlyingIndex = node.path("標的指數/追蹤指數名稱").asText("").trim();
                            String listingDateStr = node.path("上市日期").asText("");
                            LocalDateTime listingDate = RocDateUtil.parseRocDate(listingDateStr, now.minusYears(1));

                            long masterShares = parseLongSafe(node.path("發行單位數/轉換數").asText("0"));
                            NavSnapshot navSnap = navMap.get(ticker);
                            BigDecimal fundSize;
                            if (navSnap != null && navSnap.nav() != null && navSnap.sharesOutstanding() > 0) {
                                fundSize = navSnap.nav().multiply(BigDecimal.valueOf(navSnap.sharesOutstanding())).setScale(2, RoundingMode.HALF_UP);
                            } else if (navSnap != null && navSnap.nav() != null && masterShares > 0) {
                                fundSize = navSnap.nav().multiply(BigDecimal.valueOf(masterShares)).setScale(2, RoundingMode.HALF_UP);
                            } else if (masterShares > 0) {
                                fundSize = BigDecimal.valueOf(masterShares).multiply(BigDecimal.valueOf(20));
                            } else {
                                fundSize = BigDecimal.ZERO;
                            }

                            CandidateAssetClass assetClass = classifyAsset(ticker, shortName);
                            DistributionFrequency frequency = DistributionFrequency.NONE;

                            return new GlobalAssetMetadata(
                                    null, ticker, fullName, listingDate, underlyingIndex,
                                    fundSize, assetClass, frequency,
                                    1, now, now, null
                            );
                        }))
                .filter(asset -> !asset.getTicker().isBlank())
                .onErrorResume(e -> {
                    log.error("Failed to fetch TWSE ETF master universe: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }

    /**
     * Fetches daily trading quotes from TWSE concentrated market.
     */
    public Flux<MarketDailyQuote> fetchTwseDailyQuotes() {
        LocalDateTime now = LocalDateTime.now();
        return webClient.get()
                .uri(TWSE_QUOTES_URL)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .filter(node -> {
                    String code = node.path("Code").asText("").trim();
                    return code.startsWith("00"); // filter ETFs
                })
                .map(node -> {
                    String ticker = node.path("Code").asText("").trim();
                    BigDecimal openPrice = parseBigDecimalSafe(node.path("OpeningPrice").asText(""));
                    BigDecimal highPrice = parseBigDecimalSafe(node.path("HighestPrice").asText(""));
                    BigDecimal lowPrice = parseBigDecimalSafe(node.path("LowestPrice").asText(""));
                    BigDecimal closePrice = parseBigDecimalSafe(node.path("ClosingPrice").asText(""));
                    long volume = parseLongSafe(node.path("TradeVolume").asText("0"));
                    BigDecimal tradeValue = parseBigDecimalSafe(node.path("TradeValue").asText("0"));

                    return new MarketDailyQuote(
                            null, null, null, ticker, now,
                            openPrice, highPrice, lowPrice, closePrice,
                            volume, tradeValue, null, null
                    );
                })
                .filter(q -> q.getClosePrice() != null)
                .onErrorResume(e -> {
                    log.error("Failed to fetch TWSE daily quotes: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }

    /**
     * Fetches real-time / closing NAV & discount/premium percentage from TWSE MIS.
     */
    public Mono<Map<String, NavSnapshot>> fetchMisNavData() {
        return webClient.get()
                .uri(TWSE_MIS_NAV_URL)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(root -> {
                    Map<String, NavSnapshot> map = new HashMap<>();
                    JsonNode groups = root.path("a1");
                    if (groups.isArray()) {
                        for (JsonNode group : groups) {
                            JsonNode msgArray = group.path("msgArray");
                            if (msgArray.isArray()) {
                                for (JsonNode item : msgArray) {
                                    String symbol = item.path("a").asText("").trim();
                                    if (symbol.isBlank()) continue;
                                    BigDecimal nav = parseBigDecimalSafe(item.path("f").asText(""));
                                    BigDecimal discountPrem = parseBigDecimalSafe(item.path("g").asText(""));
                                    long shares = parseLongSafe(item.path("c").asText("0").split("\\.")[0]);
                                    map.put(symbol, new NavSnapshot(nav, discountPrem, shares));
                                }
                            }
                        }
                    }
                    return map;
                })
                .onErrorResume(e -> {
                    log.error("Failed to fetch TWSE MIS NAV: {}", e.getMessage(), e);
                    return Mono.just(Map.of());
                });
    }

    /**
     * Fetches regular quota (DCA) Top 20 ETF rankings from TWSE OpenAPI.
     */
    public Flux<DcaPopularityRank> fetchDcaRankings(int year, int month) {
        return webClient.get()
                .uri(TWSE_DCA_URL)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .map(node -> {
                    int rank = node.path("No").asInt(0);
                    String etfSymbol = node.path("ETFsSecurityCode").asText("").trim();
                    int accounts = (int) parseLongSafe(node.path("ETFsNumberofTradingAccounts").asText("0"));

                    return new DcaPopularityRank(
                            null, null, etfSymbol, year, month, rank, accounts
                    );
                })
                .filter(r -> !r.getTicker().isBlank() && r.getRankPosition() > 0)
                .onErrorResume(e -> {
                    log.error("Failed to fetch TWSE DCA rankings: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }

    private CandidateAssetClass classifyAsset(String ticker, String shortName) {
        if (ticker.endsWith("B") || shortName.contains("債")) {
            return CandidateAssetClass.DEFENSIVE;
        }
        return CandidateAssetClass.SATELLITE;
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

    public record NavSnapshot(BigDecimal nav, BigDecimal discountPremiumPct, long sharesOutstanding) {}
}

