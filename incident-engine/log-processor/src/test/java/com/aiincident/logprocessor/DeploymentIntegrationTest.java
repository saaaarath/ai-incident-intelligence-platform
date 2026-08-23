package com.aiincident.logprocessor;

import com.aiincident.logging.deployment.DeploymentEvent;
import com.aiincident.logprocessor.entity.ProcessedDeploymentEvent;
import com.aiincident.logprocessor.repository.DeploymentEventRepository;
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
@EmbeddedKafka(partitions = 1, topics = {"application-logs", "deployment-events"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class DeploymentIntegrationTest {

    @Autowired
    private DeploymentEventRepository deploymentEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    @DisplayName("Should consume DEPLOYMENT_STARTED and DEPLOYMENT_COMPLETED from Kafka deployment-events topic and persist")
    void testEndToEndDeploymentEventProcessing() throws Exception {
        Map<String, Object> producerProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        KafkaTemplate<String, String> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        String startEventId = "dep-start-" + UUID.randomUUID();
        DeploymentEvent startEvent = new DeploymentEvent(
                startEventId,
                "DEPLOYMENT_STARTED",
                "order-service",
                "v2.1.0",
                Instant.now(),
                "trace-dep-e2e",
                Map.of("commit", "abc1234", "author", "engineer@company.com")
        );

        String completeEventId = "dep-comp-" + UUID.randomUUID();
        DeploymentEvent completeEvent = new DeploymentEvent(
                completeEventId,
                "DEPLOYMENT_COMPLETED",
                "order-service",
                "v2.1.0",
                Instant.now(),
                "trace-dep-e2e",
                Map.of("durationMs", 45000, "status", "SUCCESS")
        );

        template.send("deployment-events", "order-service", objectMapper.writeValueAsString(startEvent));
        template.send("deployment-events", "order-service", objectMapper.writeValueAsString(completeEvent));

        // Duplicate delivery check
        template.send("deployment-events", "order-service", objectMapper.writeValueAsString(startEvent));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var events = deploymentEventRepository.findByService("order-service");
            assertThat(events).hasSize(2);
            assertThat(events).extracting(ProcessedDeploymentEvent::getEventType)
                    .containsExactlyInAnyOrder("DEPLOYMENT_STARTED", "DEPLOYMENT_COMPLETED");
        });
    }
}
