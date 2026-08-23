package com.aiincident.logging.kafka;

import com.aiincident.logging.LogEvent;
import com.aiincident.logging.LogEventPublisher;
import com.aiincident.logging.deployment.DeploymentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class KafkaLogPublisher implements LogEventPublisher, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KafkaLogPublisher.class);
    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final String deploymentTopic;
    private final boolean enabled;
    private final ObjectMapper objectMapper;

    public KafkaLogPublisher(String bootstrapServers, String topic) {
        this(bootstrapServers, topic, "deployment-events", new ObjectMapper().findAndRegisterModules());
    }

    public KafkaLogPublisher(String bootstrapServers, String topic, String deploymentTopic, ObjectMapper objectMapper) {
        this.topic = (topic == null || topic.isBlank()) ? "application-logs" : topic.trim();
        this.deploymentTopic = (deploymentTopic == null || deploymentTopic.isBlank()) ? "deployment-events" : deploymentTopic.trim();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
        if (bootstrapServers != null && !bootstrapServers.isBlank()) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers.trim());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, "1");
            props.put(ProducerConfig.RETRIES_CONFIG, 1);
            props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2000);
            this.producer = new KafkaProducer<>(props);
            this.enabled = true;
            log.info("KafkaLogPublisher initialized for topics '{}', '{}' with servers '{}'", this.topic, this.deploymentTopic, bootstrapServers);
        } else {
            this.producer = null;
            this.enabled = false;
        }
    }

    @Override
    public void publish(LogEvent event, String json) {
        if (!enabled || producer == null || json == null) {
            return;
        }
        try {
            String key = event != null && event.traceId() != null ? event.traceId() : (event != null ? event.service() : null);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.warn("Failed to publish log event to Kafka topic {}: {}", topic, exception.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("Error attempting to send log event to Kafka: {}", e.getMessage());
        }
    }

    public void publishDeploymentEvent(DeploymentEvent event) {
        if (!enabled || producer == null || event == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            String key = event.service() != null ? event.service() : event.eventId();
            ProducerRecord<String, String> record = new ProducerRecord<>(deploymentTopic, key, json);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.warn("Failed to publish deployment event to Kafka topic {}: {}", deploymentTopic, exception.getMessage());
                } else {
                    log.info("Published deployment event [{}] for service {} version {} to Kafka topic {}",
                            event.eventType(), event.service(), event.version(), deploymentTopic);
                }
            });
        } catch (Exception e) {
            log.warn("Error attempting to send deployment event to Kafka: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        if (producer != null) {
            try {
                producer.close();
            } catch (Exception ignored) {
            }
        }
    }
}
