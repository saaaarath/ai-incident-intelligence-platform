package com.aiincident.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank(message = "customerId must not be blank")
                String customerId,
                String productId,
                @Positive(message = "quantity must be greater than zero")
                Integer quantity,
                @Positive(message = "amount must be greater than zero")
                BigDecimal amount
) {
        public boolean hasBusinessFlow() {
                return productId != null || quantity != null || amount != null;
        }

        public boolean hasCompleteBusinessFlow() {
                return productId != null && !productId.isBlank() && quantity != null && amount != null;
        }
}
