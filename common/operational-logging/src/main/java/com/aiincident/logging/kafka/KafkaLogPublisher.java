package com.aiincident.logging.kafka;

import com.aiincident.logging.LogEvent;
import com.aiincident.logging.LogEventPublisher;
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
    private final boolean enabled;

    public KafkaLogPublisher(String bootstrapServers, String topic) {
        this.topic = (topic == null || topic.isBlank()) ? "application-logs" : topic.trim();
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
            log.info("KafkaLogPublisher initialized for topic '{}' with servers '{}'", this.topic, bootstrapServers);
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
