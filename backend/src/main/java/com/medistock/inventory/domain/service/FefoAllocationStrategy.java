package com.medistock.inventory.domain.service;

import com.medistock.inventory.domain.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * FEFO (First Expiry, First Out) allocation strategy.
 * <p>
 * Pure domain logic: allocates from a pre-sorted list of available batches.
 * Caller is responsible for providing batches in FEFO order
 * (expiry date ascending, then available quantity ascending).
 * </p>
 */
public final class FefoAllocationStrategy {

    /**
     * Allocate {@code requestedQuantity} units from the given batches following FEFO order.
     * <p>
     * Mutates the provided batch objects in place by calling {@link Batch#allocate(int)}.
     * </p>
     *
     * @param batches           pre-sorted list of ACTIVE batches with positive stock
     * @param productId         the product to allocate from
     * @param requestedQuantity how many units to allocate
     * @return the allocation result with line details
     * @throws StockShortageException if total available stock is insufficient
     */
    public AllocationResult allocate(List<Batch> batches, UUID productId, int requestedQuantity) {
        int totalAvailable = batches.stream()
                .mapToInt(Batch::getCurrentQuantity)
                .sum();

        if (totalAvailable < requestedQuantity) {
            throw new StockShortageException(requestedQuantity, totalAvailable);
        }

        if (totalAvailable == 0) {
            return AllocationResult.none(productId, requestedQuantity);
        }

        int remaining = requestedQuantity;
        List<AllocationLine> lines = new ArrayList<>();

        for (Batch batch : batches) {
            if (remaining <= 0) break;

            int take = Math.min(remaining, batch.getCurrentQuantity());
            batch.allocate(take);

            lines.add(new AllocationLine(
                    batch.getId(),
                    batch.getBatchCode(),
                    take,
                    batch.getCostPerUnit(),
                    batch.getCostPerUnit().multiply(java.math.BigDecimal.valueOf(take)),
                    batch.getExpirationDate()
            ));

            remaining -= take;
        }

        return AllocationResult.fromLines(productId, requestedQuantity, lines);
    }
}
