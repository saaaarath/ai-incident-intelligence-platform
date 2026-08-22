package com.aiincident.paymentservice.datasource;

import com.aiincident.failure.pool.ConnectionPoolExhaustionSimulator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link ConnectionPoolExhaustionSimulator} as a Spring bean in the
 * payment-service context. The simulator is a plain state-holder — it does not
 * wrap or replace the real DataSource, so there is no circular dependency risk.
 */
@Configuration
public class SimulatedDataSourceConfiguration {

    @Bean
    public ConnectionPoolExhaustionSimulator connectionPoolExhaustionSimulator() {
        return new ConnectionPoolExhaustionSimulator();
    }
}
