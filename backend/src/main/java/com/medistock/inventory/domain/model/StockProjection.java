package com.medistock.inventory.domain.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only projection of current stock state for a product.
 * Computed on the fly from batch data.
 */
public class StockProjection {

    private final UUID productId;
    private final int totalQuantity;
    private final int availableQuantity;
    private final int quarantinedQuantity;
    private final int expiredQuantity;
    private final int disposedQuantity;
    private final int totalBatches;
    private final LocalDate nearestExpiryDate;

    @SuppressWarnings("java:S107")
    public StockProjection(UUID productId, int totalQuantity, int availableQuantity,
                           int quarantinedQuantity, int expiredQuantity, int disposedQuantity,
                           int totalBatches, LocalDate nearestExpiryDate) {
        this.productId = productId;
        this.totalQuantity = totalQuantity;
        this.availableQuantity = availableQuantity;
        this.quarantinedQuantity = quarantinedQuantity;
        this.expiredQuantity = expiredQuantity;
        this.disposedQuantity = disposedQuantity;
        this.totalBatches = totalBatches;
        this.nearestExpiryDate = nearestExpiryDate;
    }

    public UUID getProductId() { return productId; }
    public int getTotalQuantity() { return totalQuantity; }
    public int getAvailableQuantity() { return availableQuantity; }
    public int getQuarantinedQuantity() { return quarantinedQuantity; }
    public int getExpiredQuantity() { return expiredQuantity; }
    public int getDisposedQuantity() { return disposedQuantity; }
    public int getTotalBatches() { return totalBatches; }
    public LocalDate getNearestExpiryDate() { return nearestExpiryDate; }
}
