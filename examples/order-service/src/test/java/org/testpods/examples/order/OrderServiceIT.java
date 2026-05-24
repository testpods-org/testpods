package org.testpods.examples.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testpods.examples.order.dto.CreateOrderRequest;
import org.testpods.examples.order.dto.OrderPlacedEvent;
import org.testpods.examples.order.dto.OrderResponse;
import org.testpods.examples.order.repository.OrderRepository;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sunshine integration test: boots order-service against Testcontainers Postgres + Kafka,
 * stubs the product-service via an in-test RestController on the same loopback port,
 * and verifies that POST /orders persists the order and publishes an OrderPlacedEvent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Testcontainers
class OrderServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.6.0");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private BlockingQueue<OrderPlacedEvent> capturedEvents;

    @TestConfiguration
    static class TestConfig {

        @Bean
        BlockingQueue<OrderPlacedEvent> capturedEvents() {
            return new LinkedBlockingQueue<>();
        }

        @Bean
        TestEventCollector testEventCollector(BlockingQueue<OrderPlacedEvent> capturedEvents) {
            return new TestEventCollector(capturedEvents);
        }

        @Bean
        ProductStubController productStubController() {
            return new ProductStubController();
        }
    }

    static class TestEventCollector {
        private final BlockingQueue<OrderPlacedEvent> queue;

        TestEventCollector(BlockingQueue<OrderPlacedEvent> queue) {
            this.queue = queue;
        }

        @KafkaListener(topics = "order-events", groupId = "test-consumer")
        void onEvent(OrderPlacedEvent event) {
            queue.offer(event);
        }
    }

    @RestController
    @RequestMapping("/products")
    static class ProductStubController {

        @GetMapping("/{id}")
        ResponseEntity<String> get(@PathVariable UUID id) {
            // Any GET succeeds — the order-service only checks that the response is 2xx.
            return ResponseEntity.ok("{\"id\":\"" + id + "\"}");
        }
    }

    @Test
    void createOrderPersistsAndPublishesEvent() throws Exception {
        UUID productId = UUID.randomUUID();
        RestClient client = RestClient.builder()
            .baseUrl("http://localhost:18181")
            .build();

        OrderResponse created = client.post()
            .uri("/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateOrderRequest(productId, 3))
            .retrieve()
            .body(OrderResponse.class);

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.productId()).isEqualTo(productId);
        assertThat(created.quantity()).isEqualTo(3);

        assertThat(orderRepository.findById(created.id()))
            .as("order row persisted in Postgres")
            .isPresent();

        OrderPlacedEvent event = capturedEvents.poll(15, TimeUnit.SECONDS);
        assertThat(event)
            .as("OrderPlacedEvent landed on Kafka topic order-events within 15s")
            .isNotNull();
        assertThat(event.orderId()).isEqualTo(created.id());
        assertThat(event.productId()).isEqualTo(productId);
        assertThat(event.quantity()).isEqualTo(3);
    }
}
