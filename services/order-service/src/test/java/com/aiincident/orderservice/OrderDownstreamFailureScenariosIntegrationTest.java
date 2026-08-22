package com.aiincident.orderservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aiincident.orderservice.repository.OrderRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end integration tests for the three main production failure scenarios:
 * <ol>
 *   <li><b>Payment Latency Injection</b>: Downstream payment server latency causes read timeout in order-service.
 *       Produces operational events: {@code REQUEST_TIMEOUT}, {@code PAYMENT_FAILED}.</li>
 *   <li><b>Inventory Service Unavailable</b>: Downstream inventory returns 503 Service Unavailable.
 *       Produces operational events: {@code SERVICE_UNAVAILABLE}, {@code INVENTORY_FAILURE}.</li>
 *   <li><b>Payment Error Spike</b>: Downstream payment returns 500 Internal Server Error.
 *       Produces operational events: {@code SERVICE_UNAVAILABLE}, {@code PAYMENT_FAILED}.</li>
 *   <li><b>Regression</b>: When all services are healthy, order completes with {@code SUCCESS} and {@code ORDER_CREATED}.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderDownstreamFailureScenariosIntegrationTest {

    private static HttpServer paymentServer;
    private static HttpServer inventoryServer;

    private static volatile int paymentDelayMs = 0;
    private static volatile int paymentStatusCode = 201;
    private static volatile String paymentResponseBody = "{\"id\":1001,\"orderId\":1,\"amount\":19.99,\"status\":\"SUCCESS\"}";

    private static volatile int inventoryDelayMs = 0;
    private static volatile int inventoryStatusCode = 200;
    private static volatile String inventoryResponseBody = "{\"productId\":\"prod-1\",\"availableQuantity\":10}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @DynamicPropertySource
    static void configureDownstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("payment.service.url", () -> "http://127.0.0.1:" + paymentServer.getAddress().getPort());
        registry.add("inventory.service.url", () -> "http://127.0.0.1:" + inventoryServer.getAddress().getPort());
        registry.add("downstream.connect-timeout-ms", () -> 1000);
        registry.add("downstream.read-timeout-ms", () -> 400); // 400ms read timeout for fast testing
    }

    @BeforeAll
    static void startServers() throws IOException {
        paymentServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        paymentServer.createContext("/payments", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (paymentDelayMs > 0) {
                    try {
                        Thread.sleep(paymentDelayMs);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                byte[] responseBytes = paymentResponseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(paymentStatusCode, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        });
        paymentServer.start();

        inventoryServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        inventoryServer.createContext("/inventory/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (inventoryDelayMs > 0) {
                    try {
                        Thread.sleep(inventoryDelayMs);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                byte[] responseBytes = inventoryResponseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(inventoryStatusCode, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        });
        inventoryServer.start();
    }

    @AfterAll
    static void stopServers() {
        if (paymentServer != null) {
            paymentServer.stop(0);
        }
        if (inventoryServer != null) {
            inventoryServer.stop(0);
        }
    }

    @BeforeEach
    void resetState() {
        orderRepository.deleteAll();
        paymentDelayMs = 0;
        paymentStatusCode = 201;
        paymentResponseBody = "{\"id\":1001,\"orderId\":1,\"amount\":19.99,\"status\":\"SUCCESS\"}";
        inventoryDelayMs = 0;
        inventoryStatusCode = 200;
        inventoryResponseBody = "{\"productId\":\"prod-1\",\"availableQuantity\":10}";
    }

    // -----------------------------------------------------------------------------------------
    // Scenario 1: Payment Latency Injection -> REQUEST_TIMEOUT & PAYMENT_FAILED
    // -----------------------------------------------------------------------------------------
    @Test
    void paymentLatencyInjectionCausesTimeoutAndFailsOrder() throws Exception {
        // Configure payment downstream to take 700ms (longer than 400ms read timeout)
        paymentDelayMs = 700;

        Logger orderLogger = (Logger) LoggerFactory.getLogger(com.aiincident.orderservice.service.OrderService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        orderLogger.addAppender(appender);

        try {
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":\"cust-timeout\",\"productId\":\"prod-1\",\"quantity\":1,\"amount\":\"25.00\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("FAILED"));

            List<String> logs = appender.list.stream().map(ILoggingEvent::getMessage).toList();
            assertThat(logs).anyMatch(log -> log.contains("\"eventType\":\"REQUEST_TIMEOUT\"") && log.contains("payment-service"));
            assertThat(logs).anyMatch(log -> log.contains("\"eventType\":\"PAYMENT_FAILED\""));
        } finally {
            orderLogger.detachAppender(appender);
        }
    }

    // -----------------------------------------------------------------------------------------
    // Scenario 2: Inventory Service Unavailable -> SERVICE_UNAVAILABLE & INVENTORY_FAILURE
    // -----------------------------------------------------------------------------------------
    @Test
    void inventoryServiceUnavailableFailsOrderWithStructuredEvents() throws Exception {
        // Payment succeeds, but inventory responds with 503 Service Unavailable
        inventoryStatusCode = 503;
        inventoryResponseBody = "{\"error\":\"Service is temporarily unavailable (simulated failure)\"}";

        Logger orderLogger = (Logger) LoggerFactory.getLogger(com.aiincident.orderservice.service.OrderService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        orderLogger.addAppender(appender);

        try {
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":\"cust-inv-down\",\"productId\":\"prod-1\",\"quantity\":2,\"amount\":\"50.00\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("FAILED"));

            List<String> logs = appender.list.stream().map(ILoggingEvent::getMessage).toList();
            assertThat(logs).anyMatch(log -> log.contains("\"eventType\":\"SERVICE_UNAVAILABLE\"") && log.contains("inventory-service"));
            assertThat(logs).anyMatch(log -> log.contains("\"eventType\":\"INVENTORY_FAILURE\""));
        } finally {
            orderLogger.detachAppender(appender);
        }
    }

    // -----------------------------------------------------------------------------------------
    // Scenario 3: Payment Error Spike -> SERVICE_UNAVAILABLE & PAYMENT_FAILED
    // -----------------------------------------------------------------------------------------
    @Test
    void paymentErrorSpikeFailsOrderWithStructuredEvents() throws Exception {
        // Payment responds with 500 Internal Server Error
        paymentStatusCode = 500;
        paymentResponseBody = "{\"error\":\"Simulated internal error spike\"}";

        Logger orderLogger = (Logger) LoggerFactory.getLogger(com.aiincident.orderservice.service.OrderService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        orderLogger.addAppender(appender);

        try {
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":\"cust-err-spike\",\"productId\":\"prod-1\",\"quantity\":1,\"amount\":\"10.00\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("FAILED"));

            List<String> logs = appender.list.stream().map(ILoggingEvent::getMessage).toList();
            assertThat(logs).anyMatch(log -> log.contains("\"eventType\":\"SERVICE_UNAVAILABLE\"") && log.contains("payment-service"));
            assertThat(logs).anyMatch(log -> log.contains("\"eventType\":\"PAYMENT_FAILED\""));
        } finally {
            orderLogger.detachAppender(appender);
        }
    }

    // -----------------------------------------------------------------------------------------
    // Scenario 4: Regression Test -> Normal order flow succeeds when no failures are present
    // -----------------------------------------------------------------------------------------
    @Test
    void normalOrderFlowSucceedsWhenAllFailuresAreDisabled() throws Exception {
        Logger orderLogger = (Logger) LoggerFactory.getLogger(com.aiincident.orderservice.service.OrderService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        orderLogger.addAppender(appender);

        try {
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":\"cust-healthy\",\"productId\":\"prod-1\",\"quantity\":1,\"amount\":\"15.00\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("SUCCESS"));

            List<String> logs = appender.list.stream().map(ILoggingEvent::getMessage).toList();
            assertThat(logs).anyMatch(log -> log.contains("\"eventType\":\"ORDER_CREATED\""));
            assertThat(logs).noneMatch(log -> log.contains("\"eventType\":\"PAYMENT_FAILED\""));
            assertThat(logs).noneMatch(log -> log.contains("\"eventType\":\"INVENTORY_FAILURE\""));
            assertThat(logs).noneMatch(log -> log.contains("\"eventType\":\"SERVICE_UNAVAILABLE\""));
            assertThat(logs).noneMatch(log -> log.contains("\"eventType\":\"REQUEST_TIMEOUT\""));
        } finally {
            orderLogger.detachAppender(appender);
        }
    }
}
