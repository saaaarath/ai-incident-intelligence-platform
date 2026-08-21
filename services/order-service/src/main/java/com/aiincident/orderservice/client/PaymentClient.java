package com.aiincident.orderservice.client;

import java.math.BigDecimal;

public interface PaymentClient {

    PaymentResult createPayment(Long orderId, BigDecimal amount);

    record PaymentResult(PaymentStatus status) {
        public boolean successful() {
            return status != PaymentStatus.FAILED;
        }
    }

    enum PaymentStatus {
        PENDING,
        SUCCESS,
        FAILED
    }
}