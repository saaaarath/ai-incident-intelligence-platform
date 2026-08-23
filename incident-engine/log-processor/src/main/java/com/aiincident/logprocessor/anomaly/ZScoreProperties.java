package com.aiincident.logprocessor.anomaly;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "anomaly.zscore")
public class ZScoreProperties {

    /**
     * Z-score threshold to flag an anomaly (default: 3.0 standard deviations).
     */
    private double threshold = 3.0;

    /**
     * Minimum historical baseline sample count required for Z-score calculation.
     */
    private int minSamples = 3;

    /**
     * Minimum absolute difference required to trigger an anomaly when standard deviation is zero.
     */
    private double zeroSigmaMinDiff = 0.05;

    /**
     * Whether Z-score detection is enabled.
     */
    private boolean enabled = true;

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public int getMinSamples() {
        return minSamples;
    }

    public void setMinSamples(int minSamples) {
        this.minSamples = minSamples;
    }

    public double getZeroSigmaMinDiff() {
        return zeroSigmaMinDiff;
    }

    public void setZeroSigmaMinDiff(double zeroSigmaMinDiff) {
        this.zeroSigmaMinDiff = zeroSigmaMinDiff;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
