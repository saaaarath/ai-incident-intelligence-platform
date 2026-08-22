package com.aiincident.paymentservice.datasource;

import com.aiincident.failure.pool.ConnectionPoolExhaustionSimulator;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal control endpoint for the payment-service connection pool exhaustion simulator.
 *
 * <p>Paths are under {@code /internal/} to match the bypass rule in
 * {@link com.aiincident.failure.FailureInjectionFilter} — the filter skips all
 * requests whose URI starts with {@code /internal}, so control commands are never
 * blocked by an active failure injection.
 *
 * <p>Security note: this endpoint is in the same internal namespace as
 * {@code /internal/failures}. In production it must be behind a firewall or
 * protected by the same token mechanism.
 */
@RestController
@RequestMapping("/internal/pool")
public class ConnectionPoolController {

    private final ConnectionPoolExhaustionSimulator simulator;

    public ConnectionPoolController(ConnectionPoolExhaustionSimulator simulator) {
        this.simulator = simulator;
    }

    /** Returns the current pool simulation status. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "exhausted", simulator.isExhausted()
        ));
    }

    /**
     * Enable connection pool exhaustion simulation.
     *
     * @param available the number of connections that may still be acquired
     *                  (0 = fully exhausted, default 0)
     */
    @PostMapping("/exhaust")
    public ResponseEntity<Map<String, Object>> exhaust(
            @RequestParam(name = "available", defaultValue = "0") int available) {
        simulator.enableExhaustion(available);
        return ResponseEntity.ok(Map.of(
                "exhausted", true,
                "availableConnections", available
        ));
    }

    /** Disable pool exhaustion and restore normal data-source behavior. */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> restore() {
        simulator.disableExhaustion();
        return ResponseEntity.ok(Map.of(
                "exhausted", false
        ));
    }
}
