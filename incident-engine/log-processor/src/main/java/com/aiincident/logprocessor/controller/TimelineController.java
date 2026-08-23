package com.aiincident.logprocessor.controller;

import com.aiincident.logprocessor.timeline.IncidentTimeline;
import com.aiincident.logprocessor.timeline.IncidentTimelineService;
import com.aiincident.logprocessor.timeline.TimelineEventType;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/incidents", "/api/incidents"})
public class TimelineController {

    private final IncidentTimelineService timelineService;

    public TimelineController(IncidentTimelineService timelineService) {
        this.timelineService = timelineService;
    }

    /**
     * Retrieve structured, chronologically ordered incident timeline.
     * Example: GET /incidents/1/timeline?bufferMinutes=10
     */
    @GetMapping("/{id}/timeline")
    public ResponseEntity<IncidentTimeline> getIncidentTimeline(
            @PathVariable Long id,
            @RequestParam(required = false) Integer bufferMinutes,
            @RequestParam(required = false) List<TimelineEventType> types) {

        return timelineService.buildTimeline(id, bufferMinutes, types)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
