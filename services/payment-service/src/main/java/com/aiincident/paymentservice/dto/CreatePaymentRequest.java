package com.aiincident.paymentservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotNull(message = "orderId must not be null")
        Long orderId,
        @NotNull(message = "amount must not be null")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount
) {
}
