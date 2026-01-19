package org.testpods.examples.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.testpods.examples.order.entity.Order;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
