package org.testpods.examples.order.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.testpods.examples.order.dto.CreateOrderRequest;
import org.testpods.examples.order.dto.OrderResponse;
import org.testpods.examples.order.entity.Order;
import org.testpods.examples.order.service.OrderService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return orderService.findById(id)
            .map(OrderResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        List<OrderResponse> orders = orderService.findAll().stream()
            .map(OrderResponse::from)
            .toList();
        return ResponseEntity.ok(orders);
    }

    @ExceptionHandler(OrderService.ProductNotFoundException.class)
    public ResponseEntity<String> handleProductNotFound(OrderService.ProductNotFoundException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
