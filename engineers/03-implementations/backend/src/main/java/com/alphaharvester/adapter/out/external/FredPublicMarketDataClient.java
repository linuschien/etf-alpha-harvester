package com.alphaharvester.adapter.out.external;

import com.alphaharvester.domain.entity.MacroYieldSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Public client for Federal Reserve Economic Data (FRED), St. Louis Fed.
 * Queries official, publicly available CSV graph endpoints with ZERO API KEY REQUIRED:
 * - BAMLC0A0CMEY: ICE BofA US Corporate Index Effective Yield (%)
 * - DGS10: Market Yield on U.S. Treasury Securities at 10-Year Constant Maturity (%)
 * - DGS20: Market Yield on U.S. Treasury Securities at 20-Year Constant Maturity (%)
 * - T10Y2Y: 10-Year Treasury Constant Maturity Minus 2-Year Treasury Constant Maturity (%)
 */
@Component
public class FredPublicMarketDataClient {

    private static final Logger log = LoggerFactory.getLogger(FredPublicMarketDataClient.class);
    private static final String FRED_CSV_BASE = "https://fred.stlouisfed.org/graph/fredgraph.csv?id=";

    private static final Retry RETRY_SPEC = Retry.backoff(3, Duration.ofSeconds(1))
            .maxBackoff(Duration.ofSeconds(4))
            .filter(t -> !(t instanceof WebClientResponseException e && e.getStatusCode().is4xxClientError() && e.getStatusCode().value() != 429));

    private final WebClient webClient;

    public FredPublicMarketDataClient(WebClient webClient) {
        this.webClient = webClient;
    }


    /**
     * Fetches all observations for a given series in the given date range [startDate, endDate].
     */
    public Mono<Map<LocalDate, BigDecimal>> fetchHistoricalObservations(String seriesId, LocalDate startDate, LocalDate endDate) {
        String url = FRED_CSV_BASE + seriesId + "&cosd=" + startDate + "&coed=" + endDate;
        return webClient.get()
                .uri(url)
                .header(HttpHeaders.USER_AGENT, "curl/8.5.0")
                .header(HttpHeaders.ACCEPT, "*/*")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseAllObservationsFromCsv)
                .retryWhen(RETRY_SPEC)
                .doOnError(e -> log.error("Failed to fetch historical FRED series '{}': {}", seriesId, e.getMessage(), e));
    }

    /**
     * Parses all valid observations from FRED CSV output into a sorted Map.
     */
    public Map<LocalDate, BigDecimal> parseAllObservationsFromCsv(String csvContent) {
        if (csvContent == null || csvContent.isBlank()) {
            return Collections.emptyMap();
        }
        Map<LocalDate, BigDecimal> result = new TreeMap<>();
        String[] lines = csvContent.split("\r?\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",");
            if (parts.length >= 2) {
                String valStr = parts[1].trim();
                if (!valStr.equals(".") && !valStr.isBlank()) {
                    try {
                        LocalDate date = LocalDate.parse(parts[0].trim());
                        BigDecimal val = new BigDecimal(valStr).setScale(4, RoundingMode.HALF_UP);
                        result.put(date, val);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return result;
    }

    /**
     * Assembles aligned MacroYieldSnapshots where all four series (BAMLC0A0CMEY, DGS10, DGS20, T10Y2Y)
     * are authentically present and non-null on the exact same observation date.
     * Never forward-fills or copies numbers from a previous date.
     */
    public List<MacroYieldSnapshot> assembleAlignedSnapshots(
            Map<LocalDate, BigDecimal> corpMap,
            Map<LocalDate, BigDecimal> y10Map,
            Map<LocalDate, BigDecimal> y20Map,
            Map<LocalDate, BigDecimal> spreadMap) {
        if (corpMap == null || y10Map == null || y20Map == null || spreadMap == null) {
            return Collections.emptyList();
        }

        TreeSet<LocalDate> commonDates = new TreeSet<>(corpMap.keySet());
        commonDates.retainAll(y10Map.keySet());
        commonDates.retainAll(y20Map.keySet());
        commonDates.retainAll(spreadMap.keySet());

        List<MacroYieldSnapshot> snapshots = new ArrayList<>();
        for (LocalDate date : commonDates) {
            BigDecimal corp = corpMap.get(date);
            BigDecimal y10 = y10Map.get(date);
            BigDecimal y20 = y20Map.get(date);
            BigDecimal spread = spreadMap.get(date);

            if (corp != null && y10 != null && y20 != null && spread != null) {
                snapshots.add(new MacroYieldSnapshot(
                        null,
                        date.atStartOfDay(),
                        corp,
                        y10,
                        y20,
                        spread
                ));
            }
        }
        return snapshots;
    }

    /**
     * Fetches complete 2-year (or specified range) historical macroeconomic treasury and corporate bond yields from FRED.
     */
    public Flux<MacroYieldSnapshot> fetchHistoricalMacroYields(LocalDate startDate, LocalDate endDate) {
        log.info("Fetching historical macro yields from FRED between {} and {}...", startDate, endDate);
        return Mono.zip(
                fetchHistoricalObservations("BAMLC0A0CMEY", startDate, endDate),
                fetchHistoricalObservations("DGS10", startDate, endDate),
                fetchHistoricalObservations("DGS20", startDate, endDate),
                fetchHistoricalObservations("T10Y2Y", startDate, endDate)
        ).flatMapMany(tuple -> {
            List<MacroYieldSnapshot> snapshots = assembleAlignedSnapshots(
                    tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4()
            );
            log.info("Successfully assembled {} historical MacroYieldSnapshots between {} and {}", snapshots.size(), startDate, endDate);
            return Flux.fromIterable(snapshots);
        });
    }
}
