package com.aiincident.orderservice;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiincident.orderservice.client.InventoryClient;
import com.aiincident.orderservice.client.PaymentClient;
import com.aiincident.orderservice.entity.OrderStatus;
import com.aiincident.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private PaymentClient paymentClient;

    @MockBean
    private InventoryClient inventoryClient;

    @BeforeEach
    void cleanDatabase() {
        orderRepository.deleteAll();
    }

    @Test
    void createsOrder() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"customer-123\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/orders/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.customerId").value("customer-123"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void retrievesExistingOrder() throws Exception {
        String location = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"customer-456\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("customer-456"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void returnsNotFoundForMissingOrder() throws Exception {
        mockMvc.perform(get("/orders/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Order not found: 999999"));
    }

    @Test
    void rejectsBlankCustomerId() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("customerId customerId must not be blank"));
    }

            @Test
            void completesBusinessFlowSuccessfully() throws Exception {
            when(paymentClient.createPayment(anyLong(), any())).thenReturn(
                new PaymentClient.PaymentResult(PaymentClient.PaymentStatus.SUCCESS));
            when(inventoryClient.reserve("product-1", 2)).thenReturn(true);

            mockMvc.perform(post("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"customerId\":\"customer-1\",\"productId\":\"product-1\",\"quantity\":2,\"amount\":\"12.50\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

            verify(paymentClient).createPayment(anyLong(), any());
            verify(inventoryClient).reserve("product-1", 2);
            org.assertj.core.api.Assertions.assertThat(orderRepository.findAll().getLast().getStatus()).isEqualTo(OrderStatus.SUCCESS);
            }

            @Test
            void marksOrderFailedWhenPaymentFails() throws Exception {
            when(paymentClient.createPayment(anyLong(), any())).thenReturn(
                new PaymentClient.PaymentResult(PaymentClient.PaymentStatus.FAILED));

            mockMvc.perform(post("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"customerId\":\"customer-1\",\"productId\":\"product-1\",\"quantity\":2,\"amount\":\"12.50\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));

            verify(inventoryClient, never()).reserve(any(), any(Integer.class));
            }

            @Test
            void marksOrderFailedWhenInventoryFails() throws Exception {
            when(paymentClient.createPayment(anyLong(), any())).thenReturn(
                new PaymentClient.PaymentResult(PaymentClient.PaymentStatus.SUCCESS));
            when(inventoryClient.reserve("product-1", 2)).thenThrow(new RuntimeException("stock unavailable"));

            mockMvc.perform(post("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"customerId\":\"customer-1\",\"productId\":\"product-1\",\"quantity\":2,\"amount\":\"12.50\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));
            }

            @Test
            void marksOrderFailedWhenDownstreamIsUnavailable() throws Exception {
            when(paymentClient.createPayment(anyLong(), any())).thenThrow(new RuntimeException("connection refused"));

            mockMvc.perform(post("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"customerId\":\"customer-1\",\"productId\":\"product-1\",\"quantity\":2,\"amount\":\"12.50\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));

            verify(inventoryClient, never()).reserve(any(), any(Integer.class));
            }
}
