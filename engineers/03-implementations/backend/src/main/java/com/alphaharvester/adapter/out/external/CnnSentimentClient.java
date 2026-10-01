package com.alphaharvester.adapter.out.external;

import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class CnnSentimentClient {

    private static final Logger log = LoggerFactory.getLogger(CnnSentimentClient.class);

    private static final String CNN_FEAR_GREED_URL = "https://production.dataviz.cnn.io/index/fearandgreed/graphdata";

    private static final Retry RETRY_SPEC = Retry.backoff(3, Duration.ofSeconds(1))
            .maxBackoff(Duration.ofSeconds(4))
            .filter(t -> !(t instanceof WebClientResponseException e && e.getStatusCode().is4xxClientError() && e.getStatusCode().value() != 429));

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public CnnSentimentClient(WebClient webClient, @Autowired(required = false) ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Fetches historical multi-day CNN Fear & Greed Index quotes starting from startDate (e.g. 2 years ago by default).
     */
    public Flux<MarketDailyQuote> fetchHistoricalFearAndGreedIndex(String startDate) {
        String effectiveStart = (startDate != null && !startDate.isBlank()) ? startDate : LocalDate.now().minusYears(2).toString();
        String url = CNN_FEAR_GREED_URL + "/" + effectiveStart;
        log.info("Fetching CNN Fear & Greed historical data from '{}'...", url);
        return webClient.get()
                .uri(url)
                .header(HttpHeaders.REFERER, "https://www.cnn.com/markets/fear-and-greed")
                .header(HttpHeaders.ORIGIN, "https://www.cnn.com")
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(RETRY_SPEC)
                .flatMapMany(jsonStr -> {
                    try {
                        JsonNode root = objectMapper.readTree(jsonStr);
                        JsonNode dataArray = root.path("fear_and_greed_historical").path("data");
                        if (dataArray.isMissingNode() || !dataArray.isArray() || dataArray.isEmpty()) {
                            log.warn("CNN Fear & Greed historical data missing in response");
                            return Flux.empty();
                        }

                        List<MarketDailyQuote> quotes = new ArrayList<>();
                        for (JsonNode item : dataArray) {
                            long epochMillis = item.path("x").asLong();
                            double scoreVal = item.path("y").asDouble();
                            BigDecimal score = BigDecimal.valueOf(scoreVal).setScale(2, RoundingMode.HALF_UP);
                            LocalDateTime tradeDate = LocalDateTime.ofInstant(
                                     Instant.ofEpochMilli(epochMillis), ZoneId.of("America/New_York")
                            ).toLocalDate().atStartOfDay();

                            quotes.add(new MarketDailyQuote(
                                    null, null, null, "FEAR_GREED", tradeDate,
                                    score, score, score, score,
                                    0L, BigDecimal.ZERO
                            ));
                        }
                        log.info("Successfully fetched {} historical records for CNN Fear & Greed Index.", quotes.size());
                        return Flux.fromIterable(quotes);
                    } catch (Exception e) {
                        return Flux.error(e);
                    }
                })
                .doOnError(e -> log.error("Error fetching CNN Fear & Greed historical index from '{}': {}", url, e.getMessage(), e));
    }

    /**
     * Fetches latest single CNN Fear & Greed Index score (0 to 100) and maps to MarketDailyQuote for 'FEAR_GREED'.
     */
    public Mono<MarketDailyQuote> fetchFearAndGreedIndex() {
        LocalDateTime now = LocalDateTime.now().toLocalDate().atStartOfDay();
        return webClient.get()
                .uri(CNN_FEAR_GREED_URL)
                .header(HttpHeaders.REFERER, "https://www.cnn.com/markets/fear-and-greed")
                .header(HttpHeaders.ORIGIN, "https://www.cnn.com")
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(RETRY_SPEC)
                .flatMap(jsonStr -> {
                    try {
                        JsonNode root = objectMapper.readTree(jsonStr);
                        JsonNode scoreNode = root.path("fear_and_greed").path("score");
                        if (scoreNode.isMissingNode() || scoreNode.isNull()) {
                            log.warn("CNN Fear & Greed score missing in response");
                            return Mono.error(new IllegalStateException("CNN Fear & Greed score missing in response"));
                        }
                        double scoreVal = scoreNode.asDouble();
                        BigDecimal score = BigDecimal.valueOf(scoreVal).setScale(2, RoundingMode.HALF_UP);

                        log.info("Successfully fetched CNN Fear & Greed score: {}", score);
                        MarketDailyQuote quote = new MarketDailyQuote(
                                null, null, null, "FEAR_GREED", now,
                                score, score, score, score,
                                0L, BigDecimal.ZERO
                        );
                        return Mono.just(quote);
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                .doOnError(e -> log.error("Error fetching CNN Fear & Greed index from '{}': {}", CNN_FEAR_GREED_URL, e.getMessage(), e));
    }
}

