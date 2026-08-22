package com.aiincident.failure;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/failures")
public class InternalFailureController {

    private final FailureInjectionService failureInjectionService;

    public InternalFailureController(FailureInjectionService failureInjectionService) {
        this.failureInjectionService = failureInjectionService;
    }

    @GetMapping
    public ResponseEntity<FailureConfigResponse> getStatus(
            @RequestHeader(name = "X-Internal-Token", required = false) String token) {
        if (!failureInjectionService.validateSecurityToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(failureInjectionService.getFailureConfig());
    }

    @PostMapping
    public ResponseEntity<FailureConfigResponse> enableFailure(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestBody FailureRequest request) {
        if (!failureInjectionService.validateSecurityToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!failureInjectionService.isGlobalEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        FailureType type = request != null ? request.type() : FailureType.NONE;
        Long latencyMs = request != null ? request.latencyMs() : null;
        if (request != null && Boolean.FALSE.equals(request.enabled())) {
            return ResponseEntity.ok(failureInjectionService.disableFailure());
        }
        return ResponseEntity.ok(failureInjectionService.enableFailure(type, latencyMs));
    }

    @DeleteMapping
    public ResponseEntity<FailureConfigResponse> disableFailure(
            @RequestHeader(name = "X-Internal-Token", required = false) String token) {
        if (!failureInjectionService.validateSecurityToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(failureInjectionService.disableFailure());
    }
}
