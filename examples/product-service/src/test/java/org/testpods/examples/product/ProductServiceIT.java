package org.testpods.examples.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testpods.examples.product.dto.CreateProductRequest;
import org.testpods.examples.product.dto.OrderPlacedEvent;
import org.testpods.examples.product.dto.ProductResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Sunshine integration test: boots product-service against Testcontainers Postgres + Kafka,
 * creates a product via REST, publishes an OrderPlacedEvent on Kafka, and verifies the
 * consumer decremented the stock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Testcontainers
class ProductServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.6.0");

    @Autowired
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @Test
    void orderPlacedEventDecrementsStock() throws Exception {
        RestClient client = RestClient.builder()
            .baseUrl("http://localhost:18182")
            .build();

        ProductResponse created = client.post()
            .uri("/products")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateProductRequest("Widget", 100))
            .retrieve()
            .body(ProductResponse.class);

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.stockCount()).isEqualTo(100);

        UUID productId = created.id();
        OrderPlacedEvent event = new OrderPlacedEvent(
            UUID.randomUUID(),
            productId,
            3,
            Instant.now()
        );

        kafkaTemplate.send("order-events", productId.toString(), event)
            .get(10, TimeUnit.SECONDS);

        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(200))
            .untilAsserted(() -> {
                ProductResponse current = client.get()
                    .uri("/products/{id}", productId)
                    .retrieve()
                    .body(ProductResponse.class);
                assertThat(current).isNotNull();
                assertThat(current.stockCount()).isEqualTo(97);
            });
    }
}
