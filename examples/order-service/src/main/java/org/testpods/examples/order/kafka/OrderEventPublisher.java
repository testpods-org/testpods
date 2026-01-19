package org.testpods.examples.order.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.testpods.examples.order.dto.OrderPlacedEvent;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private static final String TOPIC = "order-events";

    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(OrderPlacedEvent event) {
        log.info("Publishing OrderPlacedEvent: orderId={}, productId={}, quantity={}",
            event.orderId(), event.productId(), event.quantity());

        kafkaTemplate.send(TOPIC, event.orderId().toString(), event);
    }
}
