package com.medistock.inventory.domain.model;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable append-only ledger entry for inventory movements.
 * Once created, its state is fixed — no setters, no updates.
 */
public class Movement {

    private final UUID id;
    private final String tenantId;
    private final UUID batchId;
    private final UUID productId;
    private final MovementType type;
    private final int quantity;
    private final int previousQuantity;
    private final int newQuantity;
    private final String reason;
    private final String performedBy;
    private final LocalDateTime createdAt;

    private Movement(UUID id, String tenantId, UUID batchId, UUID productId,
                     MovementType type, int quantity, int previousQuantity, int newQuantity,
                     String reason, String performedBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.batchId = batchId;
        this.productId = productId;
        this.type = type;
        this.quantity = quantity;
        this.previousQuantity = previousQuantity;
        this.newQuantity = newQuantity;
        this.reason = reason;
        this.performedBy = performedBy;
        this.createdAt = LocalDateTime.now(Clock.systemDefaultZone());
    }

    /**
     * Factory method for creating a new movement.
     */
    public static Movement create(String tenantId, UUID batchId, UUID productId,
                                    MovementType type, int quantity,
                                    int previousQuantity, int newQuantity,
                                    String reason, String performedBy) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new InventoryDomainException("tenantId must not be blank");
        }
        if (batchId == null) {
            throw new InventoryDomainException("batchId must not be null");
        }
        if (productId == null) {
            throw new InventoryDomainException("productId must not be null");
        }
        if (type == null) {
            throw new InventoryDomainException("type must not be null");
        }
        if (quantity == 0 && previousQuantity != newQuantity) {
            throw new InventoryDomainException("quantity must not be zero when stock changes");
        }
        if (previousQuantity + quantity != newQuantity) {
            throw new InventoryDomainException(
                    "Inconsistent quantities: " + previousQuantity + " + " + quantity + " != " + newQuantity);
        }
        return new Movement(UUID.randomUUID(), tenantId, batchId, productId,
                type, quantity, previousQuantity, newQuantity, reason, performedBy);
    }

    // --- Getters only — immutable ---

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getBatchId() { return batchId; }
    public UUID getProductId() { return productId; }
    public MovementType getType() { return type; }
    public int getQuantity() { return quantity; }
    public int getPreviousQuantity() { return previousQuantity; }
    public int getNewQuantity() { return newQuantity; }
    public String getReason() { return reason; }
    public String getPerformedBy() { return performedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Movement movement)) return false;
        return Objects.equals(id, movement.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
