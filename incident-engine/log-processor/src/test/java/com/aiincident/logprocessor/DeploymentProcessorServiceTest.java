package com.aiincident.logprocessor;

import com.aiincident.logging.deployment.DeploymentEvent;
import com.aiincident.logprocessor.entity.ProcessedDeploymentEvent;
import com.aiincident.logprocessor.repository.DeploymentEventRepository;
import com.aiincident.logprocessor.service.DeploymentProcessorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DeploymentProcessorServiceTest {

        @Autowired
        private DeploymentEventRepository deploymentEventRepository;

        private DeploymentProcessorService deploymentProcessorService;
        private ObjectMapper objectMapper;

        @BeforeEach
        void setUp() {
                objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                deploymentProcessorService = new DeploymentProcessorService(deploymentEventRepository, objectMapper);
                deploymentEventRepository.deleteAll();
        }

        @Test
        @DisplayName("Should successfully process and persist DEPLOYMENT_STARTED and DEPLOYMENT_COMPLETED events")
        void testProcessValidDeploymentEvents() {
                String eventId1 = UUID.randomUUID().toString();
                DeploymentEvent started = new DeploymentEvent(
                                eventId1,
                                "DEPLOYMENT_STARTED",
                                "order-service",
                                "v1.2.0",
                                Instant.now(),
                                "trace-dep-1",
                                Map.of("deployedBy", "sre-agent", "environment", "prod"));

                Optional<ProcessedDeploymentEvent> res1 = deploymentProcessorService.processEvent(started);
                assertThat(res1).isPresent();
                ProcessedDeploymentEvent saved1 = res1.get();
                assertThat(saved1.getEventId()).isEqualTo(eventId1);
                assertThat(saved1.getEventType()).isEqualTo("DEPLOYMENT_STARTED");
                assertThat(saved1.getService()).isEqualTo("order-service");
                assertThat(saved1.getVersion()).isEqualTo("v1.2.0");
                assertThat(saved1.getTraceId()).isEqualTo("trace-dep-1");
                assertThat(saved1.getMetadata()).contains("sre-agent");

                String eventId2 = UUID.randomUUID().toString();
                DeploymentEvent completed = new DeploymentEvent(
                                eventId2,
                                "DEPLOYMENT_COMPLETED",
                                "order-service",
                                "v1.2.0",
                                Instant.now(),
                                "trace-dep-1",
                                Map.of("status", "SUCCESS"));

                Optional<ProcessedDeploymentEvent> res2 = deploymentProcessorService.processEvent(completed);
                assertThat(res2).isPresent();
                assertThat(res2.get().getEventType()).isEqualTo("DEPLOYMENT_COMPLETED");

                assertThat(deploymentEventRepository.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should safely reject invalid deployment events and missing required fields")
        void testRejectInvalidDeploymentEvents() {
                Instant now = Instant.now();

                // Missing eventId
                assertThat(deploymentProcessorService.processEvent(
                                new DeploymentEvent(null, "DEPLOYMENT_STARTED", "order-service", "v1.0", now, null,
                                                Map.of())))
                                .isEmpty();

                // Blank eventId
                assertThat(deploymentProcessorService.processEvent(
                                new DeploymentEvent("   ", "DEPLOYMENT_STARTED", "order-service", "v1.0", now, null,
                                                Map.of())))
                                .isEmpty();

                // Invalid eventType
                assertThat(deploymentProcessorService.processEvent(
                                new DeploymentEvent("e1", "INVALID_TYPE", "order-service", "v1.0", now, null,
                                                Map.of())))
                                .isEmpty();

                // Missing service
                assertThat(deploymentProcessorService.processEvent(
                                new DeploymentEvent("e2", "DEPLOYMENT_STARTED", null, "v1.0", now, null, Map.of())))
                                .isEmpty();

                // Missing version
                assertThat(deploymentProcessorService.processEvent(
                                new DeploymentEvent("e3", "DEPLOYMENT_STARTED", "order-service", "   ", now, null,
                                                Map.of())))
                                .isEmpty();

                // Missing timestamp
                assertThat(deploymentProcessorService.processEvent(
                                new DeploymentEvent("e4", "DEPLOYMENT_STARTED", "order-service", "v1.0", null, null,
                                                Map.of())))
                                .isEmpty();

                assertThat(deploymentEventRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should safely reject malformed JSON string")
        void testRejectMalformedJson() {
                assertThat(deploymentProcessorService.processRawMessage("{broken")).isEmpty();
                assertThat(deploymentProcessorService.processRawMessage("not-json")).isEmpty();
                assertThat(deploymentProcessorService.processRawMessage("")).isEmpty();
                assertThat(deploymentProcessorService.processRawMessage(null)).isEmpty();

                assertThat(deploymentEventRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should be strictly idempotent when processing identical deployment eventId multiple times")
        void testIdempotentDeploymentProcessing() {
                String eventId = "dep-dup-" + UUID.randomUUID();
                DeploymentEvent event = new DeploymentEvent(
                                eventId,
                                "DEPLOYMENT_STARTED",
                                "payment-service",
                                "v2.0.0",
                                Instant.now(),
                                "trace-p",
                                Map.of());

                Optional<ProcessedDeploymentEvent> first = deploymentProcessorService.processEvent(event);
                Optional<ProcessedDeploymentEvent> second = deploymentProcessorService.processEvent(event);
                Optional<ProcessedDeploymentEvent> third = deploymentProcessorService.processEvent(event);

                assertThat(first).isPresent();
                assertThat(second).isPresent();
                assertThat(third).isPresent();
                assertThat(second.get().getId()).isEqualTo(first.get().getId());
                assertThat(third.get().getId()).isEqualTo(first.get().getId());

                assertThat(deploymentEventRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should support querying by service, version, eventType, and traceId")
        void testQueries() {
                deploymentProcessorService.processEvent(new DeploymentEvent("e1", "DEPLOYMENT_STARTED", "service-a",
                                "v1", Instant.now(), "trace-1", Map.of()));
                deploymentProcessorService.processEvent(new DeploymentEvent("e2", "DEPLOYMENT_COMPLETED", "service-a",
                                "v1", Instant.now(), "trace-1", Map.of()));
                deploymentProcessorService.processEvent(new DeploymentEvent("e3", "DEPLOYMENT_STARTED", "service-b",
                                "v2", Instant.now(), "trace-2", Map.of()));

                List<ProcessedDeploymentEvent> serviceAEvents = deploymentProcessorService.findByService("service-a");
                assertThat(serviceAEvents).hasSize(2);

                List<ProcessedDeploymentEvent> v1Events = deploymentProcessorService.findByVersion("v1");
                assertThat(v1Events).hasSize(2);

                List<ProcessedDeploymentEvent> completedEvents = deploymentProcessorService
                                .findByEventType("DEPLOYMENT_COMPLETED");
                assertThat(completedEvents).hasSize(1);
        }
}
