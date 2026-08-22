package com.aiincident.orderservice.service;

import com.aiincident.orderservice.dto.CreateOrderRequest;
import com.aiincident.orderservice.dto.OrderResponse;
import com.aiincident.orderservice.entity.Order;
import com.aiincident.orderservice.client.InventoryClient;
import com.aiincident.orderservice.client.PaymentClient;
import com.aiincident.orderservice.repository.OrderRepository;
import com.aiincident.logging.StructuredLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;
    private final StructuredLogger operationalLogger;

    public OrderService(
            OrderRepository orderRepository,
            PaymentClient paymentClient,
            InventoryClient inventoryClient,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
        this.inventoryClient = inventoryClient;
        this.operationalLogger = new StructuredLogger(
                LoggerFactory.getLogger(OrderService.class), objectMapper, "order-service");
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = orderRepository.save(new Order(request.customerId()));
        operationalLogger.info("ORDER_CREATED", "Order created", Map.of("orderId", order.getId()));
        if (!request.hasBusinessFlow()) {
            return OrderResponse.from(order);
        }
        if (!request.hasCompleteBusinessFlow()) {
            throw new IllegalArgumentException("productId, quantity, and amount are required for the business flow");
        }

        order.markPending();
        orderRepository.save(order);

        // Step 1: Payment processing
        try {
            PaymentClient.PaymentResult payment = paymentClient.createPayment(order.getId(), request.amount());
            if (!payment.successful()) {
                operationalLogger.warn("PAYMENT_FAILED", "Payment was declined", Map.of("orderId", order.getId()));
                return fail(order);
            }
        } catch (Exception exception) {
            if (isTimeout(exception)) {
                operationalLogger.error(
                        "REQUEST_TIMEOUT", "Payment request timed out",
                        Map.of("orderId", order.getId(), "downstream", "payment-service"), exception);
                operationalLogger.error(
                        "PAYMENT_FAILED", "Payment failed due to downstream timeout",
                        Map.of("orderId", order.getId(), "reason", "timeout"), exception);
            } else {
                operationalLogger.error(
                        "SERVICE_UNAVAILABLE", "Payment service unavailable",
                        Map.of("orderId", order.getId(), "downstream", "payment-service"), exception);
                operationalLogger.error(
                        "PAYMENT_FAILED", "Payment downstream operation failed",
                        Map.of("orderId", order.getId(), "reason", "downstream_failure"), exception);
            }
            return fail(order);
        }

        // Step 2: Inventory reservation
        try {
            inventoryClient.reserve(request.productId(), request.quantity());
            order.markSuccess();
        } catch (Exception exception) {
            if (isTimeout(exception)) {
                operationalLogger.error(
                        "REQUEST_TIMEOUT", "Inventory request timed out",
                        Map.of("orderId", order.getId(), "downstream", "inventory-service"), exception);
                operationalLogger.error(
                        "INVENTORY_FAILURE", "Inventory reservation timed out",
                        Map.of("orderId", order.getId(), "productId", request.productId(), "reason", "timeout"), exception);
            } else {
                operationalLogger.error(
                        "SERVICE_UNAVAILABLE", "Inventory service unavailable",
                        Map.of("orderId", order.getId(), "downstream", "inventory-service"), exception);
                operationalLogger.error(
                        "INVENTORY_FAILURE", "Inventory reservation failed",
                        Map.of("orderId", order.getId(), "productId", request.productId()), exception);
            }
            return fail(order);
        }

        return OrderResponse.from(orderRepository.save(order));
    }

    private OrderResponse fail(Order order) {
        order.markFailed();
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private boolean isTimeout(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException
                    || current.getClass().getSimpleName().toLowerCase().contains("timeout")
                    || (current.getMessage() != null && current.getMessage().toLowerCase().contains("timed out"))
                    || (current.getMessage() != null && current.getMessage().toLowerCase().contains("timeout"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
