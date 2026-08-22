package com.aiincident.logging.kafka;

import com.aiincident.logging.StructuredLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class KafkaLoggingAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "kafka.bootstrap-servers")
    public KafkaLogPublisher kafkaLogPublisher(
            @Value("${kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${kafka.topic.application-logs:application-logs}") String topic) {
        KafkaLogPublisher publisher = new KafkaLogPublisher(bootstrapServers, topic);
        StructuredLogger.setDefaultPublisher(publisher);
        return publisher;
    }
}
