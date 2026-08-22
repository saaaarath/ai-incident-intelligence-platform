package com.aiincident.orderservice.client;

import com.aiincident.logging.trace.TraceClientHttpRequestInterceptor;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestPaymentClient implements PaymentClient {

    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public RestPaymentClient(
            @Value("${payment.service.url}") String baseUrl,
            @Value("${downstream.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${downstream.read-timeout-ms}") int readTimeoutMs,
            @org.springframework.beans.factory.annotation.Autowired(required = false) TraceClientHttpRequestInterceptor traceInterceptor) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(connectTimeoutMs, readTimeoutMs))
                .requestInterceptor(traceInterceptor != null ? traceInterceptor : new TraceClientHttpRequestInterceptor())
                .build();
    }

    public RestPaymentClient(
            String baseUrl,
            int connectTimeoutMs,
            int readTimeoutMs) {
        this(baseUrl, connectTimeoutMs, readTimeoutMs, new TraceClientHttpRequestInterceptor());
    }

    @Override
    public PaymentResult createPayment(Long orderId, BigDecimal amount) {
        try {
            PaymentResponse response = restClient.post()
                    .uri("/payments")
                    .body(new PaymentRequest(orderId, amount))
                    .retrieve()
                    .body(PaymentResponse.class);
            if (response == null || response.status() == null) {
                throw new DownstreamServiceException("Payment Service", null);
            }
            return new PaymentResult(response.status());
        } catch (DownstreamServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DownstreamServiceException("Payment Service", exception);
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return requestFactory;
    }

    private record PaymentRequest(Long orderId, BigDecimal amount) {
    }

    private record PaymentResponse(PaymentStatus status) {
        public PaymentClient.PaymentStatus status() {
            return PaymentClient.PaymentStatus.valueOf(status.name());
        }
    }
}
