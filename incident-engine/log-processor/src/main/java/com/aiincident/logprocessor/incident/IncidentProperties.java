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

    /**
     * Time window in seconds within which cascading/related events are correlated into a single incident.
     */
    private int correlationWindowSeconds = 60;

    /**
     * Maximum duration in minutes an incident can remain open for correlating incoming events.
     */
    private int maxIncidentWindowMinutes = 30;

    /**
     * Whether to automatically correlate incoming error log events into incidents.
     */
    private boolean autoCorrelateEvents = true;

    /**
     * Whether cross-service dependency correlation is enabled during cascading failures.
     */
    private boolean crossServiceCorrelationEnabled = true;

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

    public int getCorrelationWindowSeconds() {
        return correlationWindowSeconds;
    }

    public void setCorrelationWindowSeconds(int correlationWindowSeconds) {
        this.correlationWindowSeconds = correlationWindowSeconds;
    }

    public int getMaxIncidentWindowMinutes() {
        return maxIncidentWindowMinutes;
    }

    public void setMaxIncidentWindowMinutes(int maxIncidentWindowMinutes) {
        this.maxIncidentWindowMinutes = maxIncidentWindowMinutes;
    }

    public boolean isAutoCorrelateEvents() {
        return autoCorrelateEvents;
    }

    public void setAutoCorrelateEvents(boolean autoCorrelateEvents) {
        this.autoCorrelateEvents = autoCorrelateEvents;
    }

    public boolean isCrossServiceCorrelationEnabled() {
        return crossServiceCorrelationEnabled;
    }

    public void setCrossServiceCorrelationEnabled(boolean crossServiceCorrelationEnabled) {
        this.crossServiceCorrelationEnabled = crossServiceCorrelationEnabled;
    }
}
