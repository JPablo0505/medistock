package com.medistock.inventory.infrastructure.rest;

import com.medistock.inventory.domain.model.Batch;
import com.medistock.inventory.domain.model.StockProjection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BatchResponse(
        UUID id,
        String tenantId,
        UUID productId,
        String productName,
        String batchCode,
        int initialQuantity,
        int currentQuantity,
        LocalDate expirationDate,
        LocalDate manufacturingDate,
        BigDecimal costPerUnit,
        String status,
        String category,
        String notes,
        LocalDate createdAt
) {
    public static BatchResponse fromDomain(Batch batch) {
        return new BatchResponse(
                batch.getId(), batch.getTenantId(), batch.getProductId(),
                null,
                batch.getBatchCode(), batch.getInitialQuantity(), batch.getCurrentQuantity(),
                batch.getExpirationDate(), batch.getManufacturingDate(),
                batch.getCostPerUnit(), batch.getStatus().name(),
                batch.getCategory(), batch.getNotes(), null
        );
    }

    public static BatchResponse fromDomain(Batch batch, String productName) {
        return new BatchResponse(
                batch.getId(), batch.getTenantId(), batch.getProductId(),
                productName,
                batch.getBatchCode(), batch.getInitialQuantity(), batch.getCurrentQuantity(),
                batch.getExpirationDate(), batch.getManufacturingDate(),
                batch.getCostPerUnit(), batch.getStatus().name(),
                batch.getCategory(), batch.getNotes(), null
        );
    }
}
