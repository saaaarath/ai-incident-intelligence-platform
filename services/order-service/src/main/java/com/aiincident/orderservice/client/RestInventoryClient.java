package com.aiincident.orderservice.client;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestInventoryClient implements InventoryClient {

    private final RestClient restClient;

    public RestInventoryClient(
            @Value("${inventory.service.url}") String baseUrl,
            @Value("${downstream.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${downstream.read-timeout-ms}") int readTimeoutMs) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(connectTimeoutMs, readTimeoutMs))
                .build();
    }

    @Override
    public boolean reserve(String productId, int quantity) {
        try {
            restClient.post()
                    .uri("/inventory/{productId}/reserve", productId)
                    .body(new ReserveRequest(quantity))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RuntimeException exception) {
            throw new DownstreamServiceException("Inventory Service", exception);
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return requestFactory;
    }

    private record ReserveRequest(int quantity) {
    }
}
