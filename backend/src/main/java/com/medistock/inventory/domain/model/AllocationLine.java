package com.medistock.inventory.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

import java.time.LocalDate;

/**
 * A single allocation line — one batch contributed a quantity.
 */
public record AllocationLine(
        UUID batchId,
        String batchCode,
        int quantityAllocated,
        BigDecimal unitCost,
        BigDecimal totalCost,
        LocalDate expirationDate
) {
    public AllocationLine {
        if (batchId == null) throw new InventoryDomainException("batchId must not be null");
        if (quantityAllocated <= 0) throw new InventoryDomainException("quantityAllocated must be positive");
        if (unitCost == null) throw new InventoryDomainException("unitCost must not be null");
    }

    /**
     * Convenience constructor when expirationDate is not needed (legacy/backward compat).
     */
    public AllocationLine(UUID batchId, String batchCode, int quantityAllocated,
                          BigDecimal unitCost, BigDecimal totalCost) {
        this(batchId, batchCode, quantityAllocated, unitCost, totalCost, null);
    }
}
