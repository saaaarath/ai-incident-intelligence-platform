package com.aiincident.logprocessor.consumer;

import com.aiincident.logprocessor.service.LogProcessorService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LogEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LogEventConsumer.class);

    private final LogProcessorService logProcessorService;

    public LogEventConsumer(LogProcessorService logProcessorService) {
        this.logProcessorService = logProcessorService;
    }

    @KafkaListener(
            topics = "${kafka.topic.application-logs:application-logs}",
            groupId = "${spring.kafka.consumer.group-id:log-processor-group}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        try {
            String payload = record.value();
            log.debug("Received log event record from topic {} offset {}: {}", record.topic(), record.offset(), payload);
            logProcessorService.processRawMessage(payload);
        } catch (Exception e) {
            log.error("Unexpected error consuming log event record from offset {}: {}", record.offset(), e.getMessage(), e);
        }
    }
}
