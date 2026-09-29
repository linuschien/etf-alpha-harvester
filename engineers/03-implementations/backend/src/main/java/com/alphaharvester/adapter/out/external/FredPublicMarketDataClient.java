package com.alphaharvester.adapter.out.external;

import com.alphaharvester.domain.entity.MacroYieldSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
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

    private final WebClient webClient;

    public FredPublicMarketDataClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public record Observation(LocalDate date, BigDecimal value) {}

    /**
     * Fetches the latest published observation for a given FRED series ID.
     */
    public Mono<Observation> fetchLatestObservation(String seriesId) {
        String url = FRED_CSV_BASE + seriesId;
        return webClient.get()
                .uri(url)
                .header(HttpHeaders.USER_AGENT, "curl/8.5.0")
                .header(HttpHeaders.ACCEPT, "*/*")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseLatestObservationFromCsv)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                        .maxBackoff(Duration.ofSeconds(3)))
                .onErrorResume(e -> {
                    log.error("Failed to fetch FRED series '{}': {}", seriesId, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Fetches real macroeconomic treasury and corporate bond yields from FRED.
     * Zero API key required.
     */
    public Mono<MacroYieldSnapshot> fetchLatestMacroYield() {
        log.info("Fetching macroeconomic yields from FRED public CSV endpoints (Zero API Key)...");
        return Mono.zip(
                fetchLatestObservation("BAMLC0A0CMEY"),
                fetchLatestObservation("DGS10"),
                fetchLatestObservation("DGS20"),
                fetchLatestObservation("T10Y2Y")
        ).map(tuple -> {
            Observation corp = tuple.getT1();
            Observation y10 = tuple.getT2();
            Observation y20 = tuple.getT3();
            Observation spread = tuple.getT4();

            LocalDate recordDate = corp.date() != null ? corp.date() : LocalDate.now();
            LocalDateTime recordDateTime = recordDate.atStartOfDay();

            log.info("Successfully fetched FRED MacroYieldSnapshot for {}: CorpYield={}% (BAMLC0A0CMEY), 10Y={}% (DGS10), 20Y={}% (DGS20), 10Y-2Y={}% (T10Y2Y)",
                    recordDate, corp.value(), y10.value(), y20.value(), spread.value());

            return new MacroYieldSnapshot(
                    null,
                    recordDateTime,
                    corp.value(),
                    y10.value(),
                    y20.value(),
                    spread.value()
            );
        }).onErrorResume(e -> {
            log.error("Failed to fetch FRED macro yields: {}", e.getMessage(), e);
            return Mono.empty();
        });
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
                .retryWhen(Retry.backoff(3, Duration.ofMillis(500)).maxBackoff(Duration.ofSeconds(3)))
                .onErrorResume(e -> {
                    log.error("Failed to fetch historical FRED series '{}': {}", seriesId, e.getMessage());
                    return Mono.just(Collections.emptyMap());
                });
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
            Map<LocalDate, BigDecimal> corpMap = tuple.getT1();
            Map<LocalDate, BigDecimal> y10Map = tuple.getT2();
            Map<LocalDate, BigDecimal> y20Map = tuple.getT3();
            Map<LocalDate, BigDecimal> spreadMap = tuple.getT4();

            TreeSet<LocalDate> allDates = new TreeSet<>();
            allDates.addAll(corpMap.keySet());
            allDates.addAll(y10Map.keySet());
            allDates.addAll(y20Map.keySet());
            allDates.addAll(spreadMap.keySet());

            List<MacroYieldSnapshot> snapshots = new ArrayList<>();
            BigDecimal lastCorp = null;
            BigDecimal lastY10 = null;
            BigDecimal lastY20 = null;
            BigDecimal lastSpread = null;

            for (LocalDate date : allDates) {
                BigDecimal corp = corpMap.get(date);
                BigDecimal y10 = y10Map.get(date);
                BigDecimal y20 = y20Map.get(date);
                BigDecimal spread = spreadMap.get(date);

                if (corp != null) lastCorp = corp;
                if (y10 != null) lastY10 = y10;
                if (y20 != null) lastY20 = y20;
                if (spread != null) lastSpread = spread;

                // Trading days are dates where at least one treasury rate is observed
                boolean isTradingDay = (y10 != null || y20 != null || spread != null);
                if (isTradingDay && lastCorp != null && lastY10 != null && lastY20 != null && lastSpread != null) {
                    snapshots.add(new MacroYieldSnapshot(
                            null,
                            date.atStartOfDay(),
                            corp != null ? corp : lastCorp,
                            y10 != null ? y10 : lastY10,
                            y20 != null ? y20 : lastY20,
                            spread != null ? spread : lastSpread
                    ));
                }
            }

            log.info("Successfully assembled {} historical MacroYieldSnapshots between {} and {}", snapshots.size(), startDate, endDate);
            return Flux.fromIterable(snapshots);
        });
    }

    /**
     * Parses the most recent valid observation (ignoring non-trading '.' entries) from FRED CSV output.
     */
    public Optional<Observation> parseLatestObservationFromCsv(String csvContent) {
        if (csvContent == null || csvContent.isBlank()) {
            return Optional.empty();
        }
        String[] lines = csvContent.split("\r?\n");
        for (int i = lines.length - 1; i >= 1; i--) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",");
            if (parts.length >= 2) {
                String valStr = parts[1].trim();
                if (!valStr.equals(".") && !valStr.isBlank()) {
                    try {
                        LocalDate date = LocalDate.parse(parts[0].trim());
                        BigDecimal val = new BigDecimal(valStr).setScale(4, RoundingMode.HALF_UP);
                        return Optional.of(new Observation(date, val));
                    } catch (Exception ignored) {
                        // continue searching previous days
                    }
                }
            }
        }
        return Optional.empty();
    }
}
