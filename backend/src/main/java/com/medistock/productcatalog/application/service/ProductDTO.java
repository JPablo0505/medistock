package com.medistock.productcatalog.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProductDTO(
        UUID id,
        String tenantId,
        String name,
        String genericName,
        UUID categoryId,
        String concentration,
        String dosageForm,
        String manufacturer,
        boolean requiresPrescription,
        boolean isControlled,
        String storageCondition,
        String unit,
        String imageUrl,
        BigDecimal salePrice,
        BigDecimal purchasePrice,
        boolean isActive,
        LocalDate expirationDate,
        LocalDate manufacturingDate,
        int stockQuantity
) {}
