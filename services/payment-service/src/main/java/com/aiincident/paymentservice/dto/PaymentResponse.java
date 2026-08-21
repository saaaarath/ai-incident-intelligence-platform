package com.aiincident.paymentservice.dto;

import com.aiincident.paymentservice.entity.Payment;
import com.aiincident.paymentservice.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        Instant createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getAmount(),
                payment.getStatus(), payment.getCreatedAt());
    }
}
