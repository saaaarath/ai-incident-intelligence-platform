package com.aiincident.paymentservice.service;

import com.aiincident.paymentservice.dto.CreatePaymentRequest;
import com.aiincident.paymentservice.dto.PaymentResponse;
import com.aiincident.paymentservice.entity.Payment;
import com.aiincident.paymentservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public PaymentResponse createPayment(CreatePaymentRequest request) {
        return PaymentResponse.from(paymentRepository.save(new Payment(request.orderId(), request.amount())));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long id) {
        return paymentRepository.findById(id)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
