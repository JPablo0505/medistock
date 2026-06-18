package com.medistock.productcatalog.infrastructure.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for updating a product.
 */
public record UpdateProductRequest(

        @NotBlank(message = "Product name is required")
        @Size(max = 200, message = "Product name must not exceed 200 characters")
        String name,

        @Size(max = 200, message = "Generic name must not exceed 200 characters")
        String genericName,

        UUID categoryId,

        @Size(max = 100, message = "Concentration must not exceed 100 characters")
        String concentration,

        @Size(max = 50, message = "Dosage form must not exceed 50 characters")
        String dosageForm,

        @Size(max = 200, message = "Manufacturer must not exceed 200 characters")
        String manufacturer,

        boolean requiresPrescription,

        boolean isControlled,

        @Size(max = 50, message = "Storage condition must not exceed 50 characters")
        String storageCondition,

        @NotBlank(message = "Unit is required")
        @Size(max = 20, message = "Unit must not exceed 20 characters")
        String unit,

        @PositiveOrZero(message = "Sale price must be zero or positive")
        BigDecimal salePrice,

        @PositiveOrZero(message = "Purchase price must be zero or positive")
        BigDecimal purchasePrice

) {
}
