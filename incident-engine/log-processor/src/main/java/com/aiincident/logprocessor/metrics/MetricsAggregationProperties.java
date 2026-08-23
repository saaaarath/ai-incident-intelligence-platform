package com.aiincident.logprocessor.metrics;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "metrics.aggregation")
public class MetricsAggregationProperties {

    /**
     * Default aggregation time window duration in minutes (e.g. 1).
     */
    private int defaultWindowMinutes = 1;

    /**
     * Default aggregation time window duration in seconds (e.g. 60).
     */
    private int defaultWindowSeconds = 60;

    public int getDefaultWindowMinutes() {
        return defaultWindowMinutes;
    }

    public void setDefaultWindowMinutes(int defaultWindowMinutes) {
        this.defaultWindowMinutes = defaultWindowMinutes > 0 ? defaultWindowMinutes : 1;
    }

    public int getDefaultWindowSeconds() {
        return defaultWindowSeconds;
    }

    public void setDefaultWindowSeconds(int defaultWindowSeconds) {
        this.defaultWindowSeconds = defaultWindowSeconds > 0 ? defaultWindowSeconds : 60;
    }

    public Duration getDefaultWindowDuration() {
        if (defaultWindowMinutes > 0) {
            return Duration.ofMinutes(defaultWindowMinutes);
        }
        return Duration.ofSeconds(defaultWindowSeconds > 0 ? defaultWindowSeconds : 60);
    }
}
