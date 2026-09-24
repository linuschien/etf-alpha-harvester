package com.alphaharvester.web;

import com.alphaharvester.adapter.in.web.advice.GlobalRestExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalRestExceptionHandlerTest {

    private final GlobalRestExceptionHandler handler = new GlobalRestExceptionHandler();

    @Test
    @DisplayName("Should handle IllegalArgumentException with 400 Bad Request")
    void shouldHandleIllegalArgumentException() {
        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(new IllegalArgumentException("Invalid argument test"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().get("message")).isEqualTo("Invalid argument test");
    }

    @Test
    @DisplayName("Should handle general Exception with 500 Internal Server Error")
    void shouldHandleGeneralException() {
        ResponseEntity<Map<String, Object>> response = handler.handleGeneralException(new RuntimeException("Database timeout"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).isEqualTo("INTERNAL_SERVER_ERROR");
    }
}

