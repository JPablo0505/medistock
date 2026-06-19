package com.medistock.inventory.domain.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Result of a FEFO allocation operation.
 */
public final class AllocationResult {

    private final UUID productId;
    private final int requestedQuantity;
    private final int totalAllocated;
    private final boolean fullyAllocated;
    private final List<AllocationLine> lines;
    private final BigDecimal totalCost;

    private AllocationResult(UUID productId, int requestedQuantity, int totalAllocated,
                             boolean fullyAllocated, List<AllocationLine> lines, BigDecimal totalCost) {
        this.productId = Objects.requireNonNull(productId, "productId must not be null");
        this.requestedQuantity = requestedQuantity;
        this.totalAllocated = totalAllocated;
        this.fullyAllocated = fullyAllocated;
        this.lines = lines != null ? Collections.unmodifiableList(lines) : List.of();
        this.totalCost = totalCost;
    }

    /**
     * Create a result from a list of allocation lines.
     * Computes totals from the lines.
     */
    public static AllocationResult fromLines(UUID productId, int requestedQuantity,
                                              List<AllocationLine> lines) {
        if (productId == null) throw new InventoryDomainException("productId must not be null");
        if (requestedQuantity <= 0) throw new InventoryDomainException("requestedQuantity must be positive");
        int totalAllocated = lines.stream().mapToInt(AllocationLine::quantityAllocated).sum();
        BigDecimal totalCost = lines.stream()
                .map(l -> l.unitCost().multiply(BigDecimal.valueOf(l.quantityAllocated())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AllocationResult(productId, requestedQuantity, totalAllocated,
                totalAllocated >= requestedQuantity, lines, totalCost);
    }

    /**
     * Create an empty result (zero stock available).
     */
    public static AllocationResult none(UUID productId, int requestedQuantity) {
        if (productId == null) throw new InventoryDomainException("productId must not be null");
        if (requestedQuantity <= 0) throw new InventoryDomainException("requestedQuantity must be positive");
        return new AllocationResult(productId, requestedQuantity, 0, false, List.of(), BigDecimal.ZERO);
    }

    // --- Getters ---

    public UUID productId() { return productId; }
    public int requestedQuantity() { return requestedQuantity; }
    public int totalAllocated() { return totalAllocated; }
    public boolean fullyAllocated() { return fullyAllocated; }
    public List<AllocationLine> lines() { return lines; }
    public BigDecimal totalCost() { return totalCost; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AllocationResult that)) return false;
        return requestedQuantity == that.requestedQuantity
                && totalAllocated == that.totalAllocated
                && fullyAllocated == that.fullyAllocated
                && Objects.equals(productId, that.productId)
                && Objects.equals(lines, that.lines)
                && Objects.equals(totalCost, that.totalCost);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, requestedQuantity, totalAllocated, fullyAllocated, lines, totalCost);
    }
}
