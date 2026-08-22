package com.aiincident.paymentservice.datasource;

import com.aiincident.failure.FailureInjectionService;
import com.aiincident.failure.FailureLifecycleListener;
import com.aiincident.failure.FailureType;
import com.aiincident.failure.pool.ConnectionPoolExhaustionSimulator;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bridges the generic failure injection control API and the payment-service connection
 * pool simulator. Registers a {@link FailureLifecycleListener} with
 * {@link FailureInjectionService} so that posting
 * {@code {"type":"CONNECTION_POOL_EXHAUSTED"}} to {@code /internal/failures} also
 * arms the pool simulator — and a DELETE restores it.
 */
@Configuration
public class ConnectionPoolFailureListenerConfiguration {

    @Bean
    public ApplicationRunner registerConnectionPoolListener(
            FailureInjectionService failureInjectionService,
            ConnectionPoolExhaustionSimulator simulator) {
        return args -> failureInjectionService.addLifecycleListener(new FailureLifecycleListener() {
            @Override
            public void onFailureEnabled(FailureType type) {
                if (type == FailureType.CONNECTION_POOL_EXHAUSTED) {
                    // Enable full exhaustion: 0 available connections.
                    simulator.enableExhaustion(0);
                }
            }

            @Override
            public void onFailureDisabled() {
                simulator.disableExhaustion();
            }
        });
    }
}
