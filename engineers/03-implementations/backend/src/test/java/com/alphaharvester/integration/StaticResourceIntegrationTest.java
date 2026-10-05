package com.alphaharvester.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StaticResourceIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        this.webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    @Test
    @DisplayName("Should serve index.html directly from configured static dist location")
    void shouldServeFrontendIndexHtml() {
        webTestClient.get()
                .uri("/index.html")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("<!doctype html>"));
    }

    @Test
    @DisplayName("Should serve static assets directly from configured static dist location")
    void shouldServeStaticAssets() throws Exception {
        java.nio.file.Path assetsDir = java.nio.file.Paths.get("../frontend/dist/assets");
        assertThat(assetsDir).exists();

        try (var stream = java.nio.file.Files.list(assetsDir)) {
            java.nio.file.Path assetPath = stream
                    .filter(p -> p.getFileName().toString().endsWith(".css") || p.getFileName().toString().endsWith(".js"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No assets found in " + assetsDir));

            String assetFileName = assetPath.getFileName().toString();
            webTestClient.get()
                    .uri("/assets/" + assetFileName)
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Test
    @DisplayName("Should serve root GET / from configured static dist location")
    void shouldServeRootUrl() {
        webTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("<!doctype html>"));
    }
}
