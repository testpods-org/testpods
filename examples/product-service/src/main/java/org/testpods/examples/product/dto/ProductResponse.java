package org.testpods.examples.product.dto;

import org.testpods.examples.product.entity.Product;

import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    String name,
    Integer stockCount,
    Instant createdAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getName(),
            product.getStockCount(),
            product.getCreatedAt()
        );
    }
}
