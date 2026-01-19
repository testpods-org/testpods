package org.testpods.examples.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class ProductServiceClient {

    private final RestClient restClient;

    public ProductServiceClient(RestClient.Builder builder,
                                @Value("${product-service.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public boolean productExists(UUID productId) {
        try {
            restClient.get()
                .uri("/products/{id}", productId)
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
