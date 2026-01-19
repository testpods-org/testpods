package org.testpods.examples.product.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderPlacedEvent(
    UUID orderId,
    UUID productId,
    Integer quantity,
    Instant timestamp
) {
}
