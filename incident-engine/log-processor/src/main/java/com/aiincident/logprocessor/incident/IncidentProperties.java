package com.aiincident.logprocessor.incident;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "incident")
public class IncidentProperties {

    /**
     * Minimum anomaly severity to trigger automatic incident creation.
     */
    private AnomalySeverity minIncidentSeverity = AnomalySeverity.MEDIUM;

    /**
     * Whether to automatically create incidents when anomalies are detected.
     */
    private boolean autoCreateOnAnomaly = true;

    /**
     * Time window in minutes within which consecutive anomalies for the same service
     * are correlated to the active incident to prevent duplicate incidents.
     */
    private int activeWindowMinutes = 15;

    public AnomalySeverity getMinIncidentSeverity() {
        return minIncidentSeverity;
    }

    public void setMinIncidentSeverity(AnomalySeverity minIncidentSeverity) {
        this.minIncidentSeverity = minIncidentSeverity;
    }

    public boolean isAutoCreateOnAnomaly() {
        return autoCreateOnAnomaly;
    }

    public void setAutoCreateOnAnomaly(boolean autoCreateOnAnomaly) {
        this.autoCreateOnAnomaly = autoCreateOnAnomaly;
    }

    public int getActiveWindowMinutes() {
        return activeWindowMinutes;
    }

    public void setActiveWindowMinutes(int activeWindowMinutes) {
        this.activeWindowMinutes = activeWindowMinutes;
    }
}
