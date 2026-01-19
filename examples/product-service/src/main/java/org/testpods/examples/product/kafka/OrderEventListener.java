package org.testpods.examples.product.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.testpods.examples.product.dto.OrderPlacedEvent;
import org.testpods.examples.product.service.ProductService;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ProductService productService;

    public OrderEventListener(ProductService productService) {
        this.productService = productService;
    }

    @KafkaListener(topics = "order-events", groupId = "product-service")
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("Received OrderPlacedEvent: orderId={}, productId={}, quantity={}",
            event.orderId(), event.productId(), event.quantity());

        productService.decrementStock(event.productId(), event.quantity());
    }
}
