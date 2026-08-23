package com.aiincident.logging.kafka;

import com.aiincident.logging.StructuredLogger;
import com.aiincident.logging.deployment.DeploymentPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class KafkaLoggingAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "kafka.bootstrap-servers")
    public KafkaLogPublisher kafkaLogPublisher(
            @Value("${kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${kafka.topic.application-logs:application-logs}") String logTopic,
            @Value("${kafka.topic.deployment-events:deployment-events}") String deploymentTopic,
            ObjectMapper objectMapper) {
        KafkaLogPublisher publisher = new KafkaLogPublisher(bootstrapServers, logTopic, deploymentTopic, objectMapper);
        StructuredLogger.setDefaultPublisher(publisher);
        return publisher;
    }

    @Bean
    @ConditionalOnProperty(name = "kafka.bootstrap-servers")
    @ConditionalOnMissingBean
    public DeploymentPublisher deploymentPublisher(KafkaLogPublisher kafkaLogPublisher) {
        return new DeploymentPublisher(kafkaLogPublisher);
    }
}
