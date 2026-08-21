package com.aiincident.orderservice.service;

import com.aiincident.orderservice.dto.CreateOrderRequest;
import com.aiincident.orderservice.dto.OrderResponse;
import com.aiincident.orderservice.entity.Order;
import com.aiincident.orderservice.client.InventoryClient;
import com.aiincident.orderservice.client.PaymentClient;
import com.aiincident.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;

    public OrderService(OrderRepository orderRepository, PaymentClient paymentClient, InventoryClient inventoryClient) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
        this.inventoryClient = inventoryClient;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = orderRepository.save(new Order(request.customerId()));
        if (!request.hasBusinessFlow()) {
            return OrderResponse.from(order);
        }
        if (!request.hasCompleteBusinessFlow()) {
            throw new IllegalArgumentException("productId, quantity, and amount are required for the business flow");
        }

        order.markPending();
        orderRepository.save(order);
        try {
            PaymentClient.PaymentResult payment = paymentClient.createPayment(order.getId(), request.amount());
            if (!payment.successful()) {
                return fail(order);
            }
            inventoryClient.reserve(request.productId(), request.quantity());
            order.markSuccess();
        } catch (RuntimeException exception) {
            order.markFailed();
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
}
