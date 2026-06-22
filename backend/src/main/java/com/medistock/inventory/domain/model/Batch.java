package com.medistock.inventory.domain.model;

import com.medistock.shared.domain.AggregateRoot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Batch aggregate root.
 * Represents a received pharmaceutical batch tracked by FEFO engine.
 */
public class Batch implements AggregateRoot {

    private final UUID id;
    private final String tenantId;
    private final UUID productId;
    private String batchCode;
    private int initialQuantity;
    private int currentQuantity;
    private LocalDate expirationDate;
    private LocalDate manufacturingDate;
    private BigDecimal costPerUnit;
    private BatchStatus status;
    private String category;
    private String notes;

    private Batch(UUID id, String tenantId, UUID productId, String batchCode,
                  int initialQuantity, LocalDate expirationDate, BigDecimal costPerUnit) {
        this.id = id;
        this.tenantId = tenantId;
        this.productId = productId;
        this.batchCode = batchCode;
        this.initialQuantity = initialQuantity;
        this.currentQuantity = initialQuantity;
        this.expirationDate = expirationDate;
        this.costPerUnit = costPerUnit;
        this.status = BatchStatus.ACTIVE;
    }

    /**
     * Full-args constructor for persistence reconstruction only.
     * Skips validation — assumes data was validated when the batch was created.
     */
    private Batch(UUID id, String tenantId, UUID productId, String batchCode,
                  int initialQuantity, int currentQuantity, LocalDate expirationDate,
                  LocalDate manufacturingDate, BigDecimal costPerUnit,
                  BatchStatus status, String category, String notes) {
        this.id = id;
        this.tenantId = tenantId;
        this.productId = productId;
        this.batchCode = batchCode;
        this.initialQuantity = initialQuantity;
        this.currentQuantity = currentQuantity;
        this.expirationDate = expirationDate;
        this.manufacturingDate = manufacturingDate;
        this.costPerUnit = costPerUnit;
        this.status = status;
        this.category = category;
        this.notes = notes;
    }

    /**
     * Factory method to receive a new batch into inventory.
     */
    public static Batch receive(String tenantId, UUID productId, String batchCode,
                                 int quantity, LocalDate expirationDate,
                                 BigDecimal costPerUnit) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new InventoryDomainException("tenantId must not be blank");
        }
        if (productId == null) {
            throw new InventoryDomainException("productId must not be null");
        }
        if (batchCode == null || batchCode.isBlank()) {
            throw new InventoryDomainException("batchCode must not be blank");
        }
        if (quantity <= 0) {
            throw new InventoryDomainException("quantity must be positive");
        }
        if (expirationDate == null) {
            throw new InventoryDomainException("expirationDate must not be null");
        }
        if (costPerUnit == null || costPerUnit.compareTo(BigDecimal.ZERO) < 0) {
            throw new InventoryDomainException("costPerUnit must not be null or negative");
        }
        return new Batch(UUID.randomUUID(), tenantId, productId, batchCode.trim(),
                quantity, expirationDate, costPerUnit);
    }

    /**
     * Reconstruct a Batch from persisted state (no validation).
     * Public for repository use — not intended for domain logic callers.
     */
    public static Batch restore(UUID id, String tenantId, UUID productId, String batchCode,
                          int initialQuantity, int currentQuantity, LocalDate expirationDate,
                          LocalDate manufacturingDate, BigDecimal costPerUnit,
                          BatchStatus status, String category, String notes) {
        return new Batch(id, tenantId, productId, batchCode, initialQuantity,
                currentQuantity, expirationDate, manufacturingDate, costPerUnit,
                status, category, notes);
    }

    /**
     * Allocate (reduce) available quantity from this batch.
     * @throws InventoryDomainException if insufficient quantity
     */
    public void allocate(int quantity) {
        if (status != BatchStatus.ACTIVE) {
            throw new InventoryDomainException("Cannot allocate from non-ACTIVE batch (status: " + status + ")");
        }
        if (quantity <= 0) {
            throw new InventoryDomainException("Allocation quantity must be positive");
        }
        if (currentQuantity < quantity) {
            throw new InventoryDomainException(
                    "Insufficient quantity: requested " + quantity + ", available " + currentQuantity);
        }
        currentQuantity -= quantity;
        if (currentQuantity == 0) {
            status = BatchStatus.DEPLETED;
        }
    }

    /**
     * Place batch under quarantine.
     */
    public void quarantine() {
        if (status == BatchStatus.DISPOSED) {
            throw new InventoryDomainException("Cannot quarantine a DISPOSED batch");
        }
        if (status == BatchStatus.QUARANTINED) {
            throw new InventoryDomainException("Batch is already QUARANTINED");
        }
        this.status = BatchStatus.QUARANTINED;
    }

    /**
     * Release batch from quarantine back to active.
     */
    public void releaseFromQuarantine() {
        if (status != BatchStatus.QUARANTINED) {
            throw new InventoryDomainException("Batch is not QUARANTINED (status: " + status + ")");
        }
        this.status = BatchStatus.ACTIVE;
    }

    /**
     * Dispose / write off the entire batch.
     */
    public void dispose() {
        if (status == BatchStatus.DISPOSED) {
            throw new InventoryDomainException("Batch is already DISPOSED");
        }
        if (status == BatchStatus.EXPIRED) {
            throw new InventoryDomainException("Batch is already EXPIRED — mark as disposed instead");
        }
        this.status = BatchStatus.DISPOSED;
        this.currentQuantity = 0;
    }

    /**
     * Mark batch as expired.
     */
    public void markExpired() {
        if (status == BatchStatus.DISPOSED) {
            throw new InventoryDomainException("Cannot expire a DISPOSED batch");
        }
        this.status = BatchStatus.EXPIRED;
    }

    /**
     * Restore quantity to this batch (used for void/reversal).
     * Increments currentQuantity by the given amount.
     * If the batch was DEPLETED, it becomes ACTIVE again.
     */
    public void restoreQuantity(int quantity) {
        if (quantity <= 0) {
            throw new InventoryDomainException("Restore quantity must be positive");
        }
        if (status == BatchStatus.DISPOSED) {
            throw new InventoryDomainException("Cannot restore quantity to a DISPOSED batch");
        }
        this.currentQuantity += quantity;
        if (status == BatchStatus.DEPLETED && currentQuantity > 0) {
            this.status = BatchStatus.ACTIVE;
        }
    }

    /**
     * Adjust available stock level.
     */
    public void adjustQuantity(int newQuantity) {
        if (status == BatchStatus.DISPOSED || status == BatchStatus.DEPLETED) {
            throw new InventoryDomainException("Cannot adjust a " + status + " batch");
        }
        if (newQuantity < 0) {
            throw new InventoryDomainException("Quantity cannot be negative");
        }
        if (newQuantity > initialQuantity) {
            throw new InventoryDomainException("Adjustment cannot exceed initial quantity");
        }
        this.currentQuantity = newQuantity;
        if (currentQuantity == 0) {
            this.status = BatchStatus.DEPLETED;
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getProductId() { return productId; }
    public String getBatchCode() { return batchCode; }
    public int getInitialQuantity() { return initialQuantity; }
    public int getCurrentQuantity() { return currentQuantity; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public LocalDate getManufacturingDate() { return manufacturingDate; }
    public BigDecimal getCostPerUnit() { return costPerUnit; }
    public BatchStatus getStatus() { return status; }
    public String getCategory() { return category; }
    public String getNotes() { return notes; }

    // --- Setters for mutable fields ---

    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public void setManufacturingDate(LocalDate manufacturingDate) { this.manufacturingDate = manufacturingDate; }
    public void setCostPerUnit(BigDecimal costPerUnit) { this.costPerUnit = costPerUnit; }
    public void setCategory(String category) { this.category = category; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Batch batch)) return false;
        return Objects.equals(id, batch.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
