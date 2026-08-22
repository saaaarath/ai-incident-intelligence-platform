package com.aiincident.paymentservice.service;

import com.aiincident.paymentservice.dto.CreatePaymentRequest;
import com.aiincident.paymentservice.dto.PaymentResponse;
import com.aiincident.paymentservice.entity.Payment;
import com.aiincident.paymentservice.repository.PaymentRepository;
import com.aiincident.logging.StructuredLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StructuredLogger operationalLogger;

    public PaymentService(PaymentRepository paymentRepository, ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.operationalLogger = new StructuredLogger(
                LoggerFactory.getLogger(PaymentService.class), objectMapper, "payment-service");
    }

    public PaymentResponse createPayment(CreatePaymentRequest request) {
        try {
            PaymentResponse response = PaymentResponse.from(
                    paymentRepository.save(new Payment(request.orderId(), request.amount())));
            operationalLogger.info("PAYMENT_CREATED", "Payment created", Map.of("orderId", request.orderId()));
            return response;
        } catch (RuntimeException exception) {
            operationalLogger.error(
                    "DB_TIMEOUT", "Payment persistence failed", Map.of("orderId", request.orderId()), exception);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long id) {
        return paymentRepository.findById(id)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
