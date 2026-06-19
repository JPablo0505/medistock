package com.medistock.sales.domain.model;

import com.medistock.sales.domain.exception.SaleAlreadyVoidedException;
import com.medistock.sales.domain.exception.SaleDomainException;
import com.medistock.shared.domain.AggregateRoot;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Sale implements AggregateRoot {

    private final UUID saleId;
    private final String tenantId;
    private final LocalDateTime saleDate;
    private final SaleType saleType;
    private final List<SaleItem> items;
    private final BigDecimal subtotal;
    private final BigDecimal total;
    private final String performedBy;
    private SaleStatus status;
    private LocalDateTime voidedAt;
    private String voidReason;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Sale(UUID saleId, String tenantId, LocalDateTime saleDate, SaleType saleType,
                 List<SaleItem> items, BigDecimal subtotal, BigDecimal total,
                 String performedBy, SaleStatus status,
                 LocalDateTime voidedAt, String voidReason,
                 LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.saleId = saleId;
        this.tenantId = tenantId;
        this.saleDate = saleDate;
        this.saleType = saleType;
        this.items = items;
        this.subtotal = subtotal;
        this.total = total;
        this.performedBy = performedBy;
        this.status = status;
        this.voidedAt = voidedAt;
        this.voidReason = voidReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Sale create(String tenantId, String performedBy, List<SaleItem> items) {
        return create(tenantId, performedBy, items, LocalDateTime.now());
    }

    public static Sale create(String tenantId, String performedBy, List<SaleItem> items, LocalDateTime saleDate) {
        if (tenantId == null || tenantId.isBlank())
            throw new SaleDomainException("tenantId must not be blank");
        if (performedBy == null || performedBy.isBlank())
            throw new SaleDomainException("performedBy must not be blank");
        if (items == null || items.isEmpty())
            throw new SaleDomainException("Sale must have at least one item");
        if (saleDate == null)
            throw new SaleDomainException("saleDate must not be null");

        // SAL-R07: Sum of batch allocations must equal item quantity per item
        for (SaleItem item : items) {
            if (item.getAllocatedQuantitySum() != item.getQuantity()) {
                throw new SaleDomainException(
                    "Batch allocation sum (%d) does not match item quantity (%d) for product %s"
                        .formatted(item.getAllocatedQuantitySum(), item.getQuantity(), item.getProductId()));
            }
        }

        BigDecimal subtotal = items.stream()
                .map(SaleItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // SAL-R11: total = subtotal (no tax/discount for MVP)
        BigDecimal total = subtotal;

        return new Sale(
                UUID.randomUUID(),
                tenantId,
                saleDate,
                SaleType.MANUAL_ENTRY,
                Collections.unmodifiableList(items),
                subtotal,
                total,
                performedBy,
                SaleStatus.COMPLETED,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    public static Sale restore(UUID saleId, String tenantId, LocalDateTime saleDate, SaleType saleType,
                               List<SaleItem> items, BigDecimal subtotal, BigDecimal total,
                               String performedBy, SaleStatus status,
                               LocalDateTime voidedAt, String voidReason,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new Sale(saleId, tenantId, saleDate, saleType,
                items != null ? Collections.unmodifiableList(items) : List.of(),
                subtotal, total, performedBy, status, voidedAt, voidReason, createdAt, updatedAt);
    }

    // SAL-R08 / SAL-R09: Void with safety checks
    public void voidSale(String reason) {
        if (status == SaleStatus.VOIDED) {
            throw new SaleAlreadyVoidedException(saleId);
        }
        if (reason == null || reason.isBlank()) {
            throw new SaleDomainException("voidReason must not be blank");
        }
        this.status = SaleStatus.VOIDED;
        this.voidedAt = LocalDateTime.now();
        this.voidReason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    // Getters
    public UUID getSaleId() { return saleId; }
    public String getTenantId() { return tenantId; }
    public LocalDateTime getSaleDate() { return saleDate; }
    public SaleType getSaleType() { return saleType; }
    public List<SaleItem> getItems() { return items; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTotal() { return total; }
    public String getPerformedBy() { return performedBy; }
    public SaleStatus getStatus() { return status; }
    public LocalDateTime getVoidedAt() { return voidedAt; }
    public String getVoidReason() { return voidReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public int getItemCount() { return items.size(); }
}
