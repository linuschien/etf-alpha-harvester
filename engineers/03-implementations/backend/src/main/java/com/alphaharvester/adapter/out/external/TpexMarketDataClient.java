package com.alphaharvester.adapter.out.external;

import com.alphaharvester.adapter.out.external.util.RocDateUtil;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class TpexMarketDataClient {

    private static final Logger log = LoggerFactory.getLogger(TpexMarketDataClient.class);

    public static final Pattern STAGE_0_BLOCKING_PATTERN = Pattern.compile("^(00\\d{2,4}[ULRA]|02\\d{4})$");

    private static final String TPEX_QUOTES_URL = "https://www.tpex.org.tw/openapi/v1/tpex_mainboard_quotes";
    private static final String TPEX_MASTER_CSV_URL = "https://mopsfin.twse.com.tw/opendata/t187ap47_O.csv";

    private final WebClient webClient;

    public TpexMarketDataClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetches official TPEx (OTC) ETF metadata catalog from MOPS OpenData.
     * Enforces Stage 0 filter to block leveraged, inverse, futures, active, and ETN symbols.
     * Extracts authentic listing dates and underlying indices without any fake defaults.
     */
    public Flux<GlobalAssetMetadata> fetchTpexEtfMasterUniverse() {
        LocalDateTime now = LocalDateTime.now();
        return webClient.get()
                .uri(TPEX_MASTER_CSV_URL)
                .retrieve()
                .bodyToMono(String.class)
                .flatMapMany(csv -> Flux.fromIterable(parseTpexMasterCsv(csv, now)))
                .onErrorResume(e -> {
                    log.error("Failed to fetch TPEx OTC ETF master universe: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }

    private List<GlobalAssetMetadata> parseTpexMasterCsv(String csvContent, LocalDateTime now) {
        if (csvContent == null || csvContent.isBlank()) {
            return List.of();
        }
        List<GlobalAssetMetadata> list = new ArrayList<>();
        String[] lines = csvContent.split("\r?\n");
        if (lines.length < 2) {
            return List.of();
        }

        int headerIdx = -1;
        List<String> headers = null;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("基金代號")) {
                headerIdx = i;
                headers = parseCsvLine(lines[i]);
                break;
            }
        }
        if (headers == null) {
            return List.of();
        }

        int tickerCol = findColIndex(headers, "基金代號");
        int nameCol = findColIndex(headers, "基金中文名稱");
        int shortNameCol = findColIndex(headers, "基金簡稱");
        int listingDateCol = findColIndex(headers, "上市日期");
        int indexCol = findColIndex(headers, "標的指數/追蹤指數名稱");

        for (int i = headerIdx + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) continue;
            List<String> row = parseCsvLine(line);
            if (tickerCol < 0 || tickerCol >= row.size()) continue;

            String ticker = row.get(tickerCol).trim();
            if (ticker.isBlank() || STAGE_0_BLOCKING_PATTERN.matcher(ticker).matches()) {
                continue;
            }

            String fullName = (nameCol >= 0 && nameCol < row.size()) ? row.get(nameCol).trim() : "";
            if (fullName.isBlank() && shortNameCol >= 0 && shortNameCol < row.size()) {
                fullName = row.get(shortNameCol).trim();
            }

            String listingDateStr = (listingDateCol >= 0 && listingDateCol < row.size()) ? row.get(listingDateCol).trim() : "";
            LocalDateTime listingDate = RocDateUtil.parseRocDate(listingDateStr);

            String underlyingIndex = (indexCol >= 0 && indexCol < row.size()) ? row.get(indexCol).trim() : "";

            CandidateAssetClass assetClass = classifyAsset(ticker, fullName);

            list.add(new GlobalAssetMetadata(
                    null, ticker, fullName, listingDate, underlyingIndex,
                    null, assetClass, DistributionFrequency.NONE,
                    1, now, now, null
            ));
        }
        log.info("Parsed {} qualified TPEx OTC ETFs from MOPS CSV catalog.", list.size());
        return list;
    }

    private int findColIndex(List<String> headers, String colName) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).contains(colName)) {
                return i;
            }
        }
        return -1;
    }

    private List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }

    private CandidateAssetClass classifyAsset(String ticker, String name) {
        if (ticker.endsWith("B") || (name != null && name.contains("債"))) {
            return CandidateAssetClass.DEFENSIVE;
        }
        return CandidateAssetClass.SATELLITE;
    }

    /**
     * Fetches daily trading quotes from TPEx OTC market (Bond and OTC ETFs).
     * Enforces Stage 0 filter to block leveraged, inverse, futures, active, and ETN symbols.
     */
    public Flux<MarketDailyQuote> fetchTpexDailyQuotes() {
        LocalDateTime now = LocalDateTime.now();
        return webClient.get()
                .uri(TPEX_QUOTES_URL)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .filter(node -> {
                    String code = node.path("SecuritiesCompanyCode").asText("").trim();
                    return code.startsWith("00") && !STAGE_0_BLOCKING_PATTERN.matcher(code).matches();
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
