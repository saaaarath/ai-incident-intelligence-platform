package com.aiincident.logprocessor.rca;

import com.aiincident.logprocessor.anomaly.AnomalySeverity;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Model representing a candidate service evaluated as a potential primary failure vs symptom.
 */
public record PrimaryFailureCandidate(
        String service,
        double score,
        String confidence,
        boolean isPrimary,
        boolean isSymptom,
        Instant firstSeen,
        Instant lastSeen,
        long eventCount,
        String primaryEventType,
        AnomalySeverity maxSeverity,
        Map<String, Double> scoringBreakdown,
        List<String> reasons,
        List<String> symptomServices
) {}
