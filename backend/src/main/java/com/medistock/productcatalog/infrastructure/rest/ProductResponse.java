package com.medistock.productcatalog.infrastructure.rest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
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
        BigDecimal salePrice,
        BigDecimal purchasePrice,
        boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
