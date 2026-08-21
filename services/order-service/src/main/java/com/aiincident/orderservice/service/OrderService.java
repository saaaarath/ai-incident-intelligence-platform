package com.aiincident.orderservice.service;

import com.aiincident.orderservice.dto.CreateOrderRequest;
import com.aiincident.orderservice.dto.OrderResponse;
import com.aiincident.orderservice.entity.Order;
import com.aiincident.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        return OrderResponse.from(orderRepository.save(new Order(request.customerId())));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
