package com.medistock.productcatalog.infrastructure.rest;

import com.medistock.productcatalog.application.service.ProductDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for product operations.
 */
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
        LocalDateTime updatedAt,
        long version
) {

    public static ProductResponse fromDTO(ProductDTO dto) {
        return new ProductResponse(
                dto.id(),
                dto.name(),
                dto.genericName(),
                dto.categoryId(),
                dto.concentration(),
                dto.dosageForm(),
                dto.manufacturer(),
                dto.requiresPrescription(),
                dto.isControlled(),
                dto.storageCondition(),
                dto.unit(),
                dto.salePrice(),
                dto.purchasePrice(),
                dto.isActive(),
                dto.createdAt(),
                dto.updatedAt(),
                0L
        );
    }
}
