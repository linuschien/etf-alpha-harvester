package com.alphaharvester.adapter.out.external;

import com.alphaharvester.domain.entity.MarketDailyQuote;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CnnSentimentClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private CnnSentimentClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        client = new CnnSentimentClient(webClient, mapper);
    }

    @Test
    @DisplayName("Should fetch and parse latest CNN Fear & Greed index score")
    void shouldFetchFearAndGreedIndex() {
        String json = """
                {
                  "fear_and_greed": {
                    "score": 62.4,
                    "rating": "greed",
                    "timestamp": 1727740800000
                  }
                }
                """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(json));

        StepVerifier.create(client.fetchFearAndGreedIndex())
                .assertNext(quote -> {
                    assertThat(quote.getTicker()).isEqualTo("FEAR_GREED");
                    assertThat(quote.getClosePrice()).isEqualByComparingTo(new BigDecimal("62.40"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fetch and parse historical CNN Fear & Greed index data")
    void shouldFetchHistoricalFearAndGreedIndex() {
        String json = """
                {
                  "fear_and_greed_historical": {
                    "data": [
                      {
                        "x": 1727654400000,
                        "y": 60.1,
                        "rating": "greed"
                      },
                      {
                        "x": 1727740800000,
                        "y": 62.4,
                        "rating": "greed"
                      }
                    ]
                  }
                }
                """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(json));

        StepVerifier.create(client.fetchHistoricalFearAndGreedIndex("2026-09-01"))
                .assertNext(q1 -> {
                    assertThat(q1.getTicker()).isEqualTo("FEAR_GREED");
                    assertThat(q1.getClosePrice()).isEqualByComparingTo(new BigDecimal("60.10"));
                })
                .assertNext(q2 -> {
                    assertThat(q2.getTicker()).isEqualTo("FEAR_GREED");
                    assertThat(q2.getClosePrice()).isEqualByComparingTo(new BigDecimal("62.40"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should propagate error when CNN Fear & Greed endpoint fails")
    void shouldPropagateErrorWhenCnnFails() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("503 Service Unavailable")));

        StepVerifier.create(client.fetchFearAndGreedIndex())
                .expectErrorMatches(e -> (e.getMessage() != null && e.getMessage().contains("503 Service Unavailable"))
                        || (e.getCause() != null && e.getCause().getMessage().contains("503 Service Unavailable")))
                .verify();
    }
}
