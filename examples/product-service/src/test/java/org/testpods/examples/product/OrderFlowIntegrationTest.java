package org.testpods.examples.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;
import org.testpods.examples.product.dto.CreateProductRequest;
import org.testpods.examples.product.dto.OrderPlacedEvent;
import org.testpods.examples.product.dto.ProductResponse;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the order flow.
 * This test plays the role of the order-service by sending OrderPlacedEvent messages to Kafka.
 * Assumes Kafka and PostgreSQL are started externally with connection properties injected.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderFlowIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @Autowired
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();
    }

    @Test
    void shouldDecrementStockWhenOrderPlacedEventReceived() throws Exception {
        // Given: Create a product with initial stock
        int initialStock = 100;
        CreateProductRequest createRequest = new CreateProductRequest("Test Widget", initialStock);

        ProductResponse createdProduct = restClient.post()
            .uri("/products")
            .contentType(MediaType.APPLICATION_JSON)
            .body(createRequest)
            .retrieve()
            .body(ProductResponse.class);

        assertThat(createdProduct).isNotNull();
        assertThat(createdProduct.stockCount()).isEqualTo(initialStock);

        UUID productId = createdProduct.id();

        // When: Simulate order-service publishing an OrderPlacedEvent
        int orderQuantity = 3;
        OrderPlacedEvent orderEvent = new OrderPlacedEvent(
            UUID.randomUUID(),  // orderId
            productId,
            orderQuantity,
            Instant.now()
        );

        kafkaTemplate.send("order-events", productId.toString(), orderEvent).get(10, TimeUnit.SECONDS);

        // Wait for async Kafka consumer to process the event
        Thread.sleep(2000);

        // Then: Verify stock was decremented
        ProductResponse updatedProduct = restClient.get()
            .uri("/products/{id}", productId)
            .retrieve()
            .body(ProductResponse.class);

        assertThat(updatedProduct).isNotNull();
        assertThat(updatedProduct.stockCount()).isEqualTo(initialStock - orderQuantity);
    }
}
