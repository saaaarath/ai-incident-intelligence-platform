package com.aiincident.logprocessor.consumer;

import com.aiincident.logprocessor.service.DeploymentProcessorService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeploymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeploymentEventConsumer.class);

    private final DeploymentProcessorService deploymentProcessorService;

    public DeploymentEventConsumer(DeploymentProcessorService deploymentProcessorService) {
        this.deploymentProcessorService = deploymentProcessorService;
    }

    @KafkaListener(
            topics = "${kafka.topic.deployment-events:deployment-events}",
            groupId = "${spring.kafka.consumer.group-id:log-processor-group}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        try {
            String payload = record.value();
            log.debug("Received deployment event from topic {} offset {}: {}", record.topic(), record.offset(), payload);
            deploymentProcessorService.processRawMessage(payload);
        } catch (Exception e) {
            log.error("Unexpected error consuming deployment event from offset {}: {}", record.offset(), e.getMessage(), e);
        }
    }
}
