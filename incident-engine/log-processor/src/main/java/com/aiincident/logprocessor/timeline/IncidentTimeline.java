package com.aiincident.logprocessor.timeline;

import java.time.Instant;
import java.util.List;

/**
 * Model representing a unified chronological timeline for an operational incident.
 */
public record IncidentTimeline(
        Long incidentId,
        String incidentTitle,
        String primaryService,
        String rootService,
        Instant windowStart,
        Instant windowEnd,
        int totalEvents,
        List<TimelineEvent> events,
        String summary
) {}
