package org.testpods.examples.order.service;

import org.springframework.stereotype.Service;
import org.testpods.examples.order.client.ProductServiceClient;
import org.testpods.examples.order.dto.CreateOrderRequest;
import org.testpods.examples.order.dto.OrderPlacedEvent;
import org.testpods.examples.order.entity.Order;
import org.testpods.examples.order.kafka.OrderEventPublisher;
import org.testpods.examples.order.repository.OrderRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductServiceClient productServiceClient;
    private final OrderEventPublisher orderEventPublisher;

    public OrderService(OrderRepository orderRepository,
                        ProductServiceClient productServiceClient,
                        OrderEventPublisher orderEventPublisher) {
        this.orderRepository = orderRepository;
        this.productServiceClient = productServiceClient;
        this.orderEventPublisher = orderEventPublisher;
    }

    public Order createOrder(CreateOrderRequest request) {
        // Validate product exists
        if (!productServiceClient.productExists(request.productId())) {
            throw new ProductNotFoundException(request.productId());
        }

        // Create and save order
        Order order = new Order(request.productId(), request.quantity());
        order = orderRepository.save(order);

        // Publish event
        OrderPlacedEvent event = new OrderPlacedEvent(
            order.getId(),
            order.getProductId(),
            order.getQuantity(),
            Instant.now()
        );
        orderEventPublisher.publish(event);

        return order;
    }

    public Optional<Order> findById(UUID id) {
        return orderRepository.findById(id);
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(UUID productId) {
            super("Product not found: " + productId);
        }
    }
}
