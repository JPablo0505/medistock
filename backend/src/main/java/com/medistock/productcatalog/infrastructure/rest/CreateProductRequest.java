package com.medistock.productcatalog.infrastructure.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateProductRequest(
        @NotBlank String name,
        String genericName,
        UUID categoryId,
        String concentration,
        String dosageForm,
        String manufacturer,
        boolean requiresPrescription,
        boolean isControlled,
        String storageCondition,
        @NotBlank String unit,
        String imageUrl,
        @PositiveOrZero BigDecimal salePrice,
        @PositiveOrZero BigDecimal purchasePrice,
        @NotNull LocalDate expirationDate,
        @NotNull LocalDate manufacturingDate
) {}
