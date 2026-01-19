package org.testpods.examples.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.testpods.examples.product.entity.Product;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
}
