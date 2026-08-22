package com.aiincident.logging;

public interface LogEventPublisher {
    void publish(LogEvent event, String json);
}
