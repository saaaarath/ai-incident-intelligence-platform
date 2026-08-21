package com.aiincident.orderservice.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(
        @NotBlank(message = "customerId must not be blank")
        String customerId
) {
}
