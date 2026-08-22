package com.aiincident.paymentservice;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiincident.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void cleanDatabase() {
        paymentRepository.deleteAll();
    }

    @Test
    void createsPayment() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":42,\"amount\":\"19.99\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/payments/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.orderId").value(42))
                .andExpect(jsonPath("$.amount").value(19.99))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void retrievesExistingPayment() throws Exception {
        String location = mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":43,\"amount\":\"25.00\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(43))
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void returnsNotFoundForMissingPayment() throws Exception {
        mockMvc.perform(get("/payments/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Payment not found: 999999"));
    }

    @Test
    void rejectsInvalidPaymentInput() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":null,\"amount\":\"0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void createsPaymentWithGeneratedTraceId() throws Exception {
        org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger(com.aiincident.paymentservice.service.PaymentService.class);
        ch.qos.logback.classic.Logger logbackLogger = (ch.qos.logback.classic.Logger) slf4jLogger;
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender = new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        logbackLogger.addAppender(listAppender);

        try {
            org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(post("/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":100,\"amount\":\"50.00\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("X-Trace-Id"))
                    .andReturn();

            String traceId = result.getResponse().getHeader("X-Trace-Id");
            org.assertj.core.api.Assertions.assertThat(traceId).isNotBlank();

            org.assertj.core.api.Assertions.assertThat(listAppender.list).isNotEmpty();
            String loggedJson = listAppender.list.getFirst().getMessage();
            org.assertj.core.api.Assertions.assertThat(loggedJson).contains("\"traceId\":\"" + traceId + "\"");
            org.assertj.core.api.Assertions.assertThat(loggedJson).contains("\"eventType\":\"PAYMENT_CREATED\"");
        } finally {
            logbackLogger.detachAppender(listAppender);
        }
    }

    @Test
    void createsPaymentWithSuppliedTraceId() throws Exception {
        org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger(com.aiincident.paymentservice.service.PaymentService.class);
        ch.qos.logback.classic.Logger logbackLogger = (ch.qos.logback.classic.Logger) slf4jLogger;
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender = new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        logbackLogger.addAppender(listAppender);

        try {
            mockMvc.perform(post("/payments")
                            .header("X-Trace-Id", "custom-pay-trace-777")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":101,\"amount\":\"75.00\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("X-Trace-Id", "custom-pay-trace-777"));

            org.assertj.core.api.Assertions.assertThat(listAppender.list).isNotEmpty();
            String loggedJson = listAppender.list.getFirst().getMessage();
            org.assertj.core.api.Assertions.assertThat(loggedJson).contains("\"traceId\":\"custom-pay-trace-777\"");
            org.assertj.core.api.Assertions.assertThat(loggedJson).contains("\"eventType\":\"PAYMENT_CREATED\"");
        } finally {
            logbackLogger.detachAppender(listAppender);
        }
    }
}
