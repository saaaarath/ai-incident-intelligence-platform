package com.aiincident.paymentservice.service;

import com.aiincident.failure.pool.ConnectionPoolExhaustedException;
import com.aiincident.failure.pool.ConnectionPoolExhaustionSimulator;
import com.aiincident.logging.StructuredLogger;
import com.aiincident.paymentservice.dto.CreatePaymentRequest;
import com.aiincident.paymentservice.dto.PaymentResponse;
import com.aiincident.paymentservice.entity.Payment;
import com.aiincident.paymentservice.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ConnectionPoolExhaustionSimulator poolSimulator;
    private final StructuredLogger operationalLogger;

    public PaymentService(
            PaymentRepository paymentRepository,
            ConnectionPoolExhaustionSimulator poolSimulator,
            ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.poolSimulator = poolSimulator;
        this.operationalLogger = new StructuredLogger(
                LoggerFactory.getLogger(PaymentService.class), objectMapper, "payment-service");
    }

    public PaymentResponse createPayment(CreatePaymentRequest request) {
        // Attempt to acquire a connection — will throw if pool is simulated as exhausted.
        checkPoolAvailability(request.orderId());

        try {
            PaymentResponse response = PaymentResponse.from(
                    paymentRepository.save(new Payment(request.orderId(), request.amount())));
            operationalLogger.info("PAYMENT_CREATED", "Payment created", Map.of("orderId", request.orderId()));
            return response;
        } catch (org.springframework.dao.DataAccessException exception) {
            operationalLogger.error(
                    "DB_TIMEOUT", "Payment persistence failed due to database error",
                    Map.of("orderId", request.orderId()), exception);
            operationalLogger.error(
                    "PAYMENT_FAILED", "Payment failed",
                    Map.of("orderId", request.orderId(), "reason", "database_error"), null);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long id) {
        return paymentRepository.findById(id)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    /**
     * Attempt to acquire a simulated connection from the pool. When exhaustion is
     * active and no permits are available within the timeout, emits the three
     * required operational events and throws {@link ConnectionPoolExhaustedException}.
     */
    private void checkPoolAvailability(Long orderId) {
        if (!poolSimulator.isExhausted()) {
            return;
        }
        // Log events before attempting the (blocking) acquire so they always emit
        // even when the thread times out immediately.
        operationalLogger.error(
                "DB_TIMEOUT",
                "Database connection pool timeout: no connections available",
                Map.of("orderId", orderId, "poolExhausted", true),
                null);
        operationalLogger.error(
                "CONNECTION_POOL_EXHAUSTED",
                "Payment rejected: database connection pool is exhausted",
                Map.of("orderId", orderId),
                null);
        operationalLogger.error(
                "PAYMENT_FAILED",
                "Payment failed due to connection pool exhaustion",
                Map.of("orderId", orderId, "reason", "connection_pool_exhausted"),
                null);
        // This will block up to 2 s then throw ConnectionPoolExhaustedException.
        poolSimulator.acquireConnection();
    }
}
