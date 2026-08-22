package com.aiincident.failure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FailureInjectionServiceTest {

    private FailureInjectionService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .build();
        service = new FailureInjectionService(
                true,
                "secret-token",
                "NONE",
                3000L,
                "test-service",
                objectMapper);
    }

    @Test
    void initialStatusIsDisabled() {
        FailureConfigResponse config = service.getFailureConfig();
        assertThat(config.enabled()).isFalse();
        assertThat(config.type()).isEqualTo(FailureType.NONE);
    }

    @Test
    void enablesAndInjectsDbFailure() {
        service.enableFailure(FailureType.DB_FAILURE, null);
        assertThat(service.isFailureActive()).isTrue();

        assertThatThrownBy(() -> service.maybeInjectFailure())
                .isInstanceOf(SimulatedDatabaseException.class)
                .hasMessageContaining("Simulated database operation failure");
    }

    @Test
    void enablesAndInjectsServiceUnavailable() {
        service.enableFailure(FailureType.SERVICE_UNAVAILABLE, null);
        assertThat(service.isFailureActive()).isTrue();

        assertThatThrownBy(() -> service.maybeInjectFailure())
                .isInstanceOf(SimulatedServiceUnavailableException.class)
                .hasMessageContaining("Service is temporarily unavailable");
    }

    @Test
    void enablesAndInjectsErrorSpike() {
        service.enableFailure(FailureType.ERROR_SPIKE, null);
        assertThat(service.isFailureActive()).isTrue();

        assertThatThrownBy(() -> service.maybeInjectFailure())
                .isInstanceOf(SimulatedErrorSpikeException.class)
                .hasMessageContaining("Simulated internal error spike");
    }

    @Test
    void enablesAndInjectsLatency() {
        service.enableFailure(FailureType.LATENCY, 100L);
        assertThat(service.isFailureActive()).isTrue();

        long start = System.currentTimeMillis();
        service.maybeInjectFailure();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isGreaterThanOrEqualTo(90L);
    }

    @Test
    void disablesFailureAndRestoresNormalBehavior() {
        service.enableFailure(FailureType.DB_FAILURE, null);
        assertThat(service.isFailureActive()).isTrue();

        service.disableFailure();
        assertThat(service.isFailureActive()).isFalse();

        // Execution should complete without throwing
        service.maybeInjectFailure();
    }

    @Test
    void validatesSecurityToken() {
        assertThat(service.validateSecurityToken("secret-token")).isTrue();
        assertThat(service.validateSecurityToken("invalid-token")).isFalse();
        assertThat(service.validateSecurityToken(null)).isFalse();
    }

    @Test
    void connectionPoolExhaustedTypeActivatesWithoutThrowingFromFilter() {
        service.enableFailure(FailureType.CONNECTION_POOL_EXHAUSTED, null);
        assertThat(service.isFailureActive()).isTrue();
        assertThat(service.getFailureConfig().type()).isEqualTo(FailureType.CONNECTION_POOL_EXHAUSTED);

        // maybeInjectFailure must NOT throw for this type — the filter passes through
        // and the real failure happens in PaymentService via the pool simulator.
        service.maybeInjectFailure();
    }

    @Test
    void lifecycleListenerReceivesEnabledAndDisabledNotifications() {
        java.util.List<String> events = new java.util.ArrayList<>();
        service.addLifecycleListener(new FailureLifecycleListener() {
            @Override
            public void onFailureEnabled(FailureType type) {
                events.add("enabled:" + type.name());
            }

            @Override
            public void onFailureDisabled() {
                events.add("disabled");
            }
        });

        service.enableFailure(FailureType.CONNECTION_POOL_EXHAUSTED, null);
        service.disableFailure();

        assertThat(events).containsExactly("enabled:CONNECTION_POOL_EXHAUSTED", "disabled");
    }
}
