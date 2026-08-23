package com.aiincident.logprocessor.timeline;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable model representing a single chronological event in an incident timeline.
 */
public record TimelineEvent(
        String id,
        Instant timestamp,
        TimelineEventType type,
        String service,
        String summary,
        String details,
        String severity,
        String sourceEventId,
        Map<String, Object> metadata
) {}
