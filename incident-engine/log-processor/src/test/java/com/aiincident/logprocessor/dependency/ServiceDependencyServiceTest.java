package com.aiincident.logprocessor.dependency;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceDependencyServiceTest {

    @Mock
    private ServiceDependencyRepository repository;

    private ServiceDependencyService service;
    private List<ServiceDependency> database;

    @BeforeEach
    void setUp() {
        database = new ArrayList<>();
        when(repository.save(any(ServiceDependency.class))).thenAnswer(inv -> {
            ServiceDependency d = inv.getArgument(0);
            database.add(d);
            return d;
        });
        when(repository.findAll()).thenAnswer(inv -> database);
        when(repository.findBySourceService(any())).thenAnswer(inv -> {
            String src = inv.getArgument(0);
            return database.stream().filter(d -> d.getSourceService().equalsIgnoreCase(src)).toList();
        });
        when(repository.findByTargetService(any())).thenAnswer(inv -> {
            String tgt = inv.getArgument(0);
            return database.stream().filter(d -> d.getTargetService().equalsIgnoreCase(tgt)).toList();
        });
        when(repository.findBySourceServiceAndTargetService(any(), any())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            String t = inv.getArgument(1);
            return database.stream()
                    .filter(d -> d.getSourceService().equalsIgnoreCase(s) && d.getTargetService().equalsIgnoreCase(t))
                    .findFirst();
        });

        service = new ServiceDependencyService(repository);
        service.initDefaultDependencies();
    }

    @Test
    @DisplayName("Should initialize and represent initial service dependencies (Order->Payment, Payment->Inventory, Payment->Postgres)")
    void testInitialDependencies() {
        List<ServiceDependency> all = service.getAllDependencies();
        assertThat(all).isNotEmpty();

        // Verify Order -> Payment
        Optional<ServiceDependency> orderToPayment = all.stream()
                .filter(d -> d.getSourceService().equals("order-service") && d.getTargetService().equals("payment-service"))
                .findFirst();
        assertThat(orderToPayment).isPresent();

        // Verify Payment -> Inventory
        Optional<ServiceDependency> paymentToInventory = all.stream()
                .filter(d -> d.getSourceService().equals("payment-service") && d.getTargetService().equals("inventory-service"))
                .findFirst();
        assertThat(paymentToInventory).isPresent();

        // Verify Payment -> PostgreSQL
        Optional<ServiceDependency> paymentToPostgres = all.stream()
                .filter(d -> d.getSourceService().equals("payment-service") && d.getTargetService().equals("postgres"))
                .findFirst();
        assertThat(paymentToPostgres).isPresent();
    }

    @Test
    @DisplayName("Should query downstream dependencies for a service")
    void testGetDownstreamDependencies() {
        List<ServiceDependency> orderDownstream = service.getDownstream("order-service");
        List<String> targetNames = orderDownstream.stream().map(ServiceDependency::getTargetService).toList();

        assertThat(targetNames).contains("payment-service", "inventory-service");
    }

    @Test
    @DisplayName("Should query upstream callers for a service")
    void testGetUpstreamCallers() {
        List<ServiceDependency> inventoryUpstream = service.getUpstream("inventory-service");
        List<String> callerNames = inventoryUpstream.stream().map(ServiceDependency::getSourceService).toList();

        assertThat(callerNames).contains("order-service", "payment-service");
    }

    @Test
    @DisplayName("Should provide complete service topology for a service")
    void testGetServiceTopology() {
        ServiceDependencyService.ServiceTopology topology = service.getServiceTopology("payment-service");

        assertThat(topology.service()).isEqualTo("payment-service");
        assertThat(topology.downstream()).contains("inventory-service", "postgres");
        assertThat(topology.upstream()).contains("order-service");
        assertThat(topology.allRelated()).contains("order-service", "payment-service", "inventory-service", "postgres");
    }

    @Test
    @DisplayName("Should accurately determine direct and transitive service relationships")
    void testAreServicesRelated() {
        // Direct
        assertThat(service.areServicesRelated("order-service", "payment-service")).isTrue();
        assertThat(service.areServicesRelated("payment-service", "inventory-service")).isTrue();

        // Transitive
        assertThat(service.areServicesRelated("order-service", "postgres")).isTrue();

        // Unrelated service
        assertThat(service.areServicesRelated("order-service", "notification-service")).isFalse();
    }

    @Test
    @DisplayName("Should dynamically add and remove service dependencies")
    void testAddAndRemoveDependency() {
        when(repository.existsBySourceServiceAndTargetService("notification-service", "email-gateway")).thenReturn(true);

        ServiceDependency added = service.addDependency("notification-service", "email-gateway", ServiceDependencyType.HTTP_REST, "Email dispatch");
        assertThat(added.getSourceService()).isEqualTo("notification-service");
        assertThat(added.getTargetService()).isEqualTo("email-gateway");

        boolean removed = service.removeDependency("notification-service", "email-gateway");
        assertThat(removed).isTrue();
        verify(repository).deleteBySourceServiceAndTargetService("notification-service", "email-gateway");
    }
}
