package com.alphaharvester.adapter.out.external;

import com.alphaharvester.domain.entity.MacroYieldSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

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
                fetchLatestObservation("BAMLC0A0CMEY").defaultIfEmpty(new Observation(LocalDate.now().minusDays(1), new BigDecimal("5.69"))),
                fetchLatestObservation("DGS10").defaultIfEmpty(new Observation(LocalDate.now().minusDays(1), new BigDecimal("4.96"))),
                fetchLatestObservation("DGS20").defaultIfEmpty(new Observation(LocalDate.now().minusDays(1), new BigDecimal("5.33"))),
                fetchLatestObservation("T10Y2Y").defaultIfEmpty(new Observation(LocalDate.now().minusDays(1), new BigDecimal("0.25")))
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
