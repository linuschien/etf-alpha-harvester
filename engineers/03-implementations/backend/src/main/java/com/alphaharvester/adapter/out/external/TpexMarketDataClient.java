package com.alphaharvester.adapter.out.external;

import com.alphaharvester.adapter.out.external.util.RocDateUtil;
import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.alphaharvester.domain.entity.GlobalAssetMetadata;
import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class TpexMarketDataClient {

    private static final Logger log = LoggerFactory.getLogger(TpexMarketDataClient.class);

    private static final Retry RETRY_SPEC = Retry.backoff(3, Duration.ofSeconds(1))
            .maxBackoff(Duration.ofSeconds(4))
            .filter(t -> !(t instanceof WebClientResponseException e && e.getStatusCode().is4xxClientError() && e.getStatusCode().value() != 429));

    public static final Pattern STAGE_0_ALLOWLIST_PATTERN = Pattern.compile("^00\\d{2,4}B?$");

    private static final String TPEX_QUOTES_URL = "https://www.tpex.org.tw/openapi/v1/tpex_mainboard_quotes";
    private static final String TPEX_MASTER_CSV_URL = "https://mopsfin.twse.com.tw/opendata/t187ap47_O.csv";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public TpexMarketDataClient(WebClient webClient, @Autowired(required = false) ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
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
                .retryWhen(RETRY_SPEC)
                .flatMapMany(csv -> Flux.fromIterable(parseTpexMasterCsv(csv, now)))
                .doOnError(e -> log.error("Failed to fetch TPEx OTC ETF master universe: {}", e.getMessage(), e));
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
            if (ticker.isBlank() || !STAGE_0_ALLOWLIST_PATTERN.matcher(ticker).matches()) {
                continue;
            }

            String fullName = (nameCol >= 0 && nameCol < row.size()) ? row.get(nameCol).trim() : "";
            if (fullName.isBlank() && shortNameCol >= 0 && shortNameCol < row.size()) {
                fullName = row.get(shortNameCol).trim();
            }

            String listingDateStr = (listingDateCol >= 0 && listingDateCol < row.size()) ? row.get(listingDateCol).trim() : "";
            LocalDateTime listingDate = RocDateUtil.parseRocDate(listingDateStr);

            String underlyingIndex = (indexCol >= 0 && indexCol < row.size()) ? row.get(indexCol).trim() : "";

            list.add(new GlobalAssetMetadata(
                    null, ticker, fullName, listingDate, underlyingIndex,
                    null, now, now, null
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

    /**
     * Fetches daily trading quotes from TPEx OTC market (Bond and OTC ETFs).
     * Enforces Stage 0 filter to block leveraged, inverse, futures, active, and ETN symbols.
     */
    public Flux<MarketDailyQuote> fetchTpexDailyQuotes() {
        LocalDateTime now = LocalDateTime.now().toLocalDate().atStartOfDay();
        return webClient.get()
                .uri(TPEX_QUOTES_URL)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(RETRY_SPEC)
                .flatMapMany(jsonStr -> {
                    try {
                        JsonNode root = objectMapper.readTree(jsonStr);
                        return (root != null && root.isArray()) ? Flux.fromIterable(root) : Flux.empty();
                    } catch (Exception e) {
                        return Flux.error(e);
                    }
                })
                .filter(node -> {
                    String code = node.path("SecuritiesCompanyCode").asText("").trim();
                    return STAGE_0_ALLOWLIST_PATTERN.matcher(code).matches();
                })
                .map(node -> {
                    String ticker = node.path("SecuritiesCompanyCode").asText("").trim();
                    String dateStr = node.path("Date").asText("");
                    LocalDateTime tradeDate = RocDateUtil.parseRocDate(dateStr, now);
                    if (tradeDate != null) {
                        tradeDate = tradeDate.toLocalDate().atStartOfDay();
                    }

                    BigDecimal openPrice = parseBigDecimalSafe(node.path("Open").asText(""));
                    BigDecimal highPrice = parseBigDecimalSafe(node.path("High").asText(""));
                    BigDecimal lowPrice = parseBigDecimalSafe(node.path("Low").asText(""));
                    BigDecimal closePrice = parseBigDecimalSafe(node.path("Close").asText(""));
                    long volume = parseLongSafe(node.path("TradingShares").asText("0"));
                    BigDecimal tradeValue = parseBigDecimalSafe(node.path("TransactionAmount").asText("0"));

                    return new MarketDailyQuote(
                            null, null, null, ticker, tradeDate,
                            openPrice, highPrice, lowPrice, closePrice,
                            volume, tradeValue
                    );
                })
                .filter(q -> q.getClosePrice() != null)
                .doOnError(e -> log.error("Failed to fetch TPEx OTC daily quotes: {}", e.getMessage(), e));
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
