package com.aiincident.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LogEventTest {

    @Test
    void serializedEventContainsAllRequiredFields() throws Exception {
        LogEvent event = LogEvent.create(
                "order-service",
                "INFO",
                "ORDER_CREATED",
                "trace-123",
                "Order created",
                Map.of("orderId", 42L));

        ObjectMapper objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        Map<String, Object> json = objectMapper.readValue(
                objectMapper.writeValueAsString(event), Map.class);

        assertThat(json).containsKeys(
                "eventId", "timestamp", "service", "level", "eventType", "traceId", "message", "metadata");
    }
}