package com.aiincident.orderservice.dto;

import com.aiincident.orderservice.entity.Order;
import com.aiincident.orderservice.entity.OrderStatus;
import java.time.Instant;

public record OrderResponse(
        Long id,
        String customerId,
        OrderStatus status,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus(), order.getCreatedAt());
    }
}
