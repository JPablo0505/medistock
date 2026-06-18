package com.medistock.inventory.infrastructure.rest;

import com.medistock.inventory.domain.model.StockProjection;
import java.time.LocalDate;
import java.util.UUID;

public record StockProjectionResponse(
        UUID productId,
        int totalQuantity,
        int availableQuantity,
        int quarantinedQuantity,
        int expiredQuantity,
        int disposedQuantity,
        int totalBatches,
        LocalDate nearestExpiryDate
) {
    public static StockProjectionResponse fromDomain(StockProjection projection) {
        return new StockProjectionResponse(
                projection.getProductId(), projection.getTotalQuantity(),
                projection.getAvailableQuantity(), projection.getQuarantinedQuantity(),
                projection.getExpiredQuantity(), projection.getDisposedQuantity(),
                projection.getTotalBatches(), projection.getNearestExpiryDate()
        );
    }
}
