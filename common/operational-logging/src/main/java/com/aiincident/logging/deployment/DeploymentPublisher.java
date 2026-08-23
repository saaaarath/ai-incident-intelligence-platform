package com.aiincident.logging.deployment;

import com.aiincident.logging.kafka.KafkaLogPublisher;
import java.util.Map;

public class DeploymentPublisher {

    private final KafkaLogPublisher kafkaLogPublisher;

    public DeploymentPublisher(KafkaLogPublisher kafkaLogPublisher) {
        this.kafkaLogPublisher = kafkaLogPublisher;
    }

    public void publishStarted(String service, String version, String traceId, Map<String, Object> metadata) {
        DeploymentEvent event = DeploymentEvent.started(service, version, traceId, metadata);
        publish(event);
    }

    public void publishCompleted(String service, String version, String traceId, Map<String, Object> metadata) {
        DeploymentEvent event = DeploymentEvent.completed(service, version, traceId, metadata);
        publish(event);
    }

    public void publish(DeploymentEvent event) {
        if (kafkaLogPublisher != null && event != null) {
            kafkaLogPublisher.publishDeploymentEvent(event);
        }
    }
}
