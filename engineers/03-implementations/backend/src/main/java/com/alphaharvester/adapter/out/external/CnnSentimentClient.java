package com.alphaharvester.adapter.out.external;

import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Component
public class CnnSentimentClient {

    private static final Logger log = LoggerFactory.getLogger(CnnSentimentClient.class);

    private static final String CNN_FEAR_GREED_URL = "https://production.dataviz.cnn.io/index/fearandgreed/graphdata";

    private final WebClient webClient;

    public CnnSentimentClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetches CNN Fear & Greed Index score (0 to 100) and maps to MarketDailyQuote for 'FEAR_GREED'.
     */
    public Mono<MarketDailyQuote> fetchFearAndGreedIndex() {
        LocalDateTime now = LocalDateTime.now();
        return webClient.get()
                .uri(CNN_FEAR_GREED_URL)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(root -> {
                    try {
                        JsonNode scoreNode = root.path("fear_and_greed").path("score");
                        if (scoreNode.isMissingNode() || scoreNode.isNull()) {
                            log.warn("CNN Fear & Greed score missing in response");
                            return Mono.empty();
                        }
                        double scoreVal = scoreNode.asDouble();
                        BigDecimal score = BigDecimal.valueOf(scoreVal).setScale(2, RoundingMode.HALF_UP);

                        log.info("Successfully fetched CNN Fear & Greed score: {}", score);
                        MarketDailyQuote quote = new MarketDailyQuote(
                                null, null, null, "FEAR_GREED", now,
                                score, score, score, score,
                                0L, BigDecimal.ZERO, null, null
                        );
                        return Mono.just(quote);
                    } catch (Exception e) {
                        log.error("Failed to parse CNN Fear & Greed response: {}", e.getMessage(), e);
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error fetching CNN Fear & Greed index from '{}': {}", CNN_FEAR_GREED_URL, e.getMessage());
                    return Mono.empty();
                });
    }
}

