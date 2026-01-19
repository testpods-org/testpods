package org.testpods.examples.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateProductRequest(
    @NotBlank String name,
    @Min(0) Integer stockCount
) {
}
