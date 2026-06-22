package com.medistock.sales.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "batch_allocations", schema = "sales")
public class JpaBatchAllocationEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private JpaSaleItemEntity item;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "batch_code", nullable = false, length = 100)
    private String batchCode;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "allocated_quantity", nullable = false)
    private int allocatedQuantity;

    @Column(name = "cost_per_unit", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPerUnit;

    public JpaBatchAllocationEntity() {
        // Required by JPA
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public JpaSaleItemEntity getItem() { return item; }
    public void setItem(JpaSaleItemEntity item) { this.item = item; }

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }

    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

    public LocalDate getExpirationDate() { return expirationDate; }
    public void setExpirationDate(LocalDate expirationDate) { this.expirationDate = expirationDate; }

    public int getAllocatedQuantity() { return allocatedQuantity; }
    public void setAllocatedQuantity(int allocatedQuantity) { this.allocatedQuantity = allocatedQuantity; }

    public BigDecimal getCostPerUnit() { return costPerUnit; }
    public void setCostPerUnit(BigDecimal costPerUnit) { this.costPerUnit = costPerUnit; }
}
