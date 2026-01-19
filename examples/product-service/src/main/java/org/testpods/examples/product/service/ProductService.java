package org.testpods.examples.product.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.testpods.examples.product.dto.CreateProductRequest;
import org.testpods.examples.product.entity.Product;
import org.testpods.examples.product.repository.ProductRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product createProduct(CreateProductRequest request) {
        Product product = new Product(request.name(), request.stockCount());
        return productRepository.save(product);
    }

    public Optional<Product> findById(UUID id) {
        return productRepository.findById(id);
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Transactional
    public void decrementStock(UUID productId, int quantity) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            log.error("Product not found: {}", productId);
            return;
        }

        int oldStock = product.getStockCount();
        int newStock = oldStock - quantity;
        if (newStock < 0) {
            log.error("Insufficient stock for product {}: requested {}, available {}",
                productId, quantity, oldStock);
        }

        product.setStockCount(newStock);
        productRepository.save(product);
        log.info("Decremented stock for product {}: {} -> {}", productId, oldStock, newStock);
    }
}
