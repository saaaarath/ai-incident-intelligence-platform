package com.aiincident.logprocessor.anomaly;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "anomaly.detection")
public class AnomalyDetectionProperties {

    /**
     * Number of standard deviations from baseline mean to trigger an anomaly (e.g. 3.0).
     */
    private double sigmaThreshold = 3.0;

    /**
     * Absolute error rate threshold (e.g. 0.05 = 5%) to trigger anomaly when baseline has 0 errors.
     */
    private double errorRateAbsoluteThreshold = 0.05;

    /**
     * Multiplier over baseline mean to trigger latency anomaly when variability is near zero (e.g. 2.0 = 2x baseline).
     */
    private double latencySpikeMultiplier = 2.0;

    /**
     * Minimum baseline samples required for statistical evaluation.
     */
    private int minBaselineSamples = 3;

    /**
     * Default historical baseline lookback in minutes.
     */
    private int defaultBaselineMinutes = 15;

    public double getSigmaThreshold() {
        return sigmaThreshold;
    }

    public void setSigmaThreshold(double sigmaThreshold) {
        this.sigmaThreshold = sigmaThreshold;
    }

    public double getErrorRateAbsoluteThreshold() {
        return errorRateAbsoluteThreshold;
    }

    public void setErrorRateAbsoluteThreshold(double errorRateAbsoluteThreshold) {
        this.errorRateAbsoluteThreshold = errorRateAbsoluteThreshold;
    }

    public double getLatencySpikeMultiplier() {
        return latencySpikeMultiplier;
    }

    public void setLatencySpikeMultiplier(double latencySpikeMultiplier) {
        this.latencySpikeMultiplier = latencySpikeMultiplier;
    }

    public int getMinBaselineSamples() {
        return minBaselineSamples;
    }

    public void setMinBaselineSamples(int minBaselineSamples) {
        this.minBaselineSamples = minBaselineSamples;
    }

    public int getDefaultBaselineMinutes() {
        return defaultBaselineMinutes;
    }

    public void setDefaultBaselineMinutes(int defaultBaselineMinutes) {
        this.defaultBaselineMinutes = defaultBaselineMinutes;
    }
}
