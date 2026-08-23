package com.aiincident.logprocessor.rca;

import java.time.Instant;
import java.util.List;

/**
 * Root Cause Analysis report distinguishing the likely primary failure from downstream symptoms.
 */
public record PrimaryFailureAnalysis(
        Long incidentId,
        PrimaryFailureCandidate primaryCandidate,
        List<PrimaryFailureCandidate> allCandidates,
        List<PrimaryFailureCandidate> symptoms,
        Instant analyzedAt,
        String summary
) {}
