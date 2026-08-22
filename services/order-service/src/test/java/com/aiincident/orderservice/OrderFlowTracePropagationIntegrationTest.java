package com.aiincident.orderservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aiincident.logging.StructuredLogger;
import com.aiincident.logging.trace.TraceConstants;
import com.aiincident.logging.trace.TraceContext;
import com.aiincident.orderservice.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderFlowTracePropagationIntegrationTest {

    private static HttpServer paymentServer;
    private static HttpServer inventoryServer;

    private static final List<String> paymentReceivedTraceIds = new CopyOnWriteArrayList<>();
    private static final List<String> inventoryReceivedTraceIds = new CopyOnWriteArrayList<>();

    private static volatile boolean paymentShouldSucceed = true;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureDownstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("payment.service.url", () -> "http://127.0.0.1:" + paymentServer.getAddress().getPort());
        registry.add("inventory.service.url", () -> "http://127.0.0.1:" + inventoryServer.getAddress().getPort());
        registry.add("downstream.connect-timeout-ms", () -> 3000);
        registry.add("downstream.read-timeout-ms", () -> 3000);
    }

    @BeforeAll
    static void startServers() throws IOException {
        paymentServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        paymentServer.createContext("/payments", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String traceId = exchange.getRequestHeaders().getFirst(TraceConstants.TRACE_HEADER);
                if (traceId != null) {
                    paymentReceivedTraceIds.add(traceId);
                }

                String responseBody;
                if (paymentShouldSucceed) {
                    responseBody = "{\"id\":1001,\"orderId\":1,\"amount\":19.99,\"status\":\"SUCCESS\",\"createdAt\":\"2026-08-22T20:00:00Z\"}";
                } else {
                    responseBody = "{\"id\":1001,\"orderId\":1,\"amount\":19.99,\"status\":\"FAILED\",\"createdAt\":\"2026-08-22T20:00:00Z\"}";
                }

                byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                if (traceId != null) {
                    exchange.getResponseHeaders().set(TraceConstants.TRACE_HEADER, traceId);
                }
                exchange.sendResponseHeaders(201, responseBytes.length);
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
                String traceId = exchange.getRequestHeaders().getFirst(TraceConstants.TRACE_HEADER);
                if (traceId != null) {
                    inventoryReceivedTraceIds.add(traceId);
                }

                String responseBody = "{\"productId\":\"product-10\",\"availableQuantity\":8,\"updatedAt\":\"2026-08-22T20:00:00Z\"}";
                byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                if (traceId != null) {
                    exchange.getResponseHeaders().set(TraceConstants.TRACE_HEADER, traceId);
                }
                exchange.sendResponseHeaders(200, responseBytes.length);
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
    void setup() {
        orderRepository.deleteAll();
        paymentReceivedTraceIds.clear();
        inventoryReceivedTraceIds.clear();
        paymentShouldSucceed = true;
    }

    @Test
    void propagatesSuppliedTraceIdAcrossEntireOrderFlow() throws Exception {
        Logger orderLogger = (Logger) LoggerFactory.getLogger(com.aiincident.orderservice.service.OrderService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        orderLogger.addAppender(appender);

        String suppliedTraceId = "flow-trace-xyz-98765";

        try {
            MvcResult result = mockMvc.perform(post("/orders")
                            .header(TraceConstants.TRACE_HEADER, suppliedTraceId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":\"cust-flow-1\",\"productId\":\"product-10\",\"quantity\":2,\"amount\":\"19.99\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(TraceConstants.TRACE_HEADER, suppliedTraceId))
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andReturn();

            // Verify downstream HTTP propagation
            assertThat(paymentReceivedTraceIds).containsExactly(suppliedTraceId);
            assertThat(inventoryReceivedTraceIds).containsExactly(suppliedTraceId);

            // Verify order service logs carry the exact same trace ID
            List<String> orderLogs = appender.list.stream().map(ILoggingEvent::getMessage).toList();
            assertThat(orderLogs).isNotEmpty();
            for (String log : orderLogs) {
                assertThat(log).contains("\"traceId\":\"" + suppliedTraceId + "\"");
            }
        } finally {
            orderLogger.detachAppender(appender);
        }
    }

    @Test
    void generatesTraceIdAndPropagatesAcrossEntireOrderFlowWhenNotSupplied() throws Exception {
        Logger orderLogger = (Logger) LoggerFactory.getLogger(com.aiincident.orderservice.service.OrderService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        orderLogger.addAppender(appender);

        try {
            MvcResult result = mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":\"cust-flow-2\",\"productId\":\"product-10\",\"quantity\":1,\"amount\":\"10.00\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists(TraceConstants.TRACE_HEADER))
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andReturn();

            String generatedTraceId = result.getResponse().getHeader(TraceConstants.TRACE_HEADER);
            assertThat(generatedTraceId).isNotBlank();

            // Verify downstream HTTP propagation
            assertThat(paymentReceivedTraceIds).containsExactly(generatedTraceId);
            assertThat(inventoryReceivedTraceIds).containsExactly(generatedTraceId);

            // Verify order service logs carry the generated trace ID
            List<String> orderLogs = appender.list.stream().map(ILoggingEvent::getMessage).toList();
            assertThat(orderLogs).isNotEmpty();
            for (String log : orderLogs) {
                assertThat(log).contains("\"traceId\":\"" + generatedTraceId + "\"");
            }
        } finally {
            orderLogger.detachAppender(appender);
        }
    }

    @Test
    void propagatesTraceIdToFailureLogsWhenPaymentFails() throws Exception {
        paymentShouldSucceed = false;

        Logger orderLogger = (Logger) LoggerFactory.getLogger(com.aiincident.orderservice.service.OrderService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        orderLogger.addAppender(appender);

        String failureTraceId = "trace-payment-failure-404";

        try {
            mockMvc.perform(post("/orders")
                            .header(TraceConstants.TRACE_HEADER, failureTraceId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":\"cust-fail-1\",\"productId\":\"product-10\",\"quantity\":1,\"amount\":\"5.00\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(TraceConstants.TRACE_HEADER, failureTraceId))
                    .andExpect(jsonPath("$.status").value("FAILED"));

            assertThat(paymentReceivedTraceIds).containsExactly(failureTraceId);
            assertThat(inventoryReceivedTraceIds).isEmpty(); // should not call inventory when payment fails

            List<String> orderLogs = appender.list.stream().map(ILoggingEvent::getMessage).toList();
            assertThat(orderLogs).isNotEmpty();
            assertThat(orderLogs).anyMatch(log -> log.contains("\"eventType\":\"PAYMENT_FAILED\"") && log.contains("\"traceId\":\"" + failureTraceId + "\""));
        } finally {
            orderLogger.detachAppender(appender);
        }
    }
}
