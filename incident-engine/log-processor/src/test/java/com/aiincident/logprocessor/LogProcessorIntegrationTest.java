package com.aiincident.logprocessor;

import com.aiincident.logging.LogEvent;
import com.aiincident.logprocessor.entity.ProcessedLogEvent;
import com.aiincident.logprocessor.repository.LogEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"application-logs"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class LogProcessorIntegrationTest {

    @Autowired
    private LogEventRepository logEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    @DisplayName("Should consume message from Kafka application-logs topic and persist to repository")
    void testEndToEndKafkaConsumption() throws Exception {
        Map<String, Object> producerProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        KafkaTemplate<String, String> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        String traceId = "test-trace-" + UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        LogEvent event = new LogEvent(
                eventId,
                Instant.now(),
                "order-service",
                "INFO",
                "ORDER_CREATED",
                traceId,
                "Order created from e2e test",
                Map.of("orderId", 999)
        );

        String json = objectMapper.writeValueAsString(event);
        template.send("application-logs", traceId, json);

        // Send a malformed message as well to verify it does not break the consumer
        template.send("application-logs", "broken-key", "{malformed json");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var logs = logEventRepository.findByTraceId(traceId);
            assertThat(logs).isNotEmpty();
            ProcessedLogEvent saved = logs.getFirst();
            assertThat(saved.getEventId()).isEqualTo(eventId);
            assertThat(saved.getService()).isEqualTo("order-service");
            assertThat(saved.getEventType()).isEqualTo("ORDER_CREATED");
        });
    }

    @Test
    @DisplayName("Should handle duplicate message delivery via Kafka and maintain exactly one stored record")
    void testDuplicateKafkaDeliveryIdempotency() throws Exception {
        Map<String, Object> producerProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        KafkaTemplate<String, String> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        String traceId = "test-dup-trace-" + UUID.randomUUID();
        String eventId = "dup-kafka-" + UUID.randomUUID();
        LogEvent event = new LogEvent(
                eventId,
                Instant.now(),
                "payment-service",
                "INFO",
                "PAYMENT_CREATED",
                traceId,
                "Payment message sent multiple times",
                Map.of("amount", 75.0)
        );

        String json = objectMapper.writeValueAsString(event);

        // Send the same message 3 times over Kafka
        template.send("application-logs", traceId, json);
        template.send("application-logs", traceId, json);
        template.send("application-logs", traceId, json);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var logs = logEventRepository.findByTraceId(traceId);
            assertThat(logs).hasSize(1);
            assertThat(logs.getFirst().getEventId()).isEqualTo(eventId);
        });

        // Ensure after additional wait time it still remains strictly 1 record
        Thread.sleep(1000);
        assertThat(logEventRepository.findByTraceId(traceId)).hasSize(1);
    }
}
