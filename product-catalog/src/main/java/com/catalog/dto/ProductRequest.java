package com.catalog.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "SKU is required")
        @Size(max = 64, message = "SKU must be at most 64 characters")
        String sku,

        @NotBlank(message = "Product name is required")
        @Size(max = 255, message = "Product name must be at most 255 characters")
        String name,

        String description,

        @NotBlank(message = "Category is required")
        @Size(max = 100, message = "Category must be at most 100 characters")
        String category,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Price must have up to 10 integer digits and 2 decimal places")
        BigDecimal price,

        @Min(value = 0, message = "Stock quantity cannot be negative")
        int stockQuantity
) {}
