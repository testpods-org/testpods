package org.testpods.examples.order.dto;

import org.testpods.examples.order.entity.Order;
import org.testpods.examples.order.entity.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    UUID productId,
    Integer quantity,
    OrderStatus status,
    Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getProductId(),
            order.getQuantity(),
            order.getStatus(),
            order.getCreatedAt()
        );
    }
}
