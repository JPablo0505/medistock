package com.medistock.sales.domain.model;

import com.medistock.shared.domain.ValueObject;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class SaleItem implements ValueObject {

    private final UUID itemId;
    private final UUID productId;
    private final String productName;
    private final String productSku;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal totalPrice;
    private final List<BatchAllocation> batchAllocations;

    public SaleItem(UUID productId, String productName, String productSku,
                    int quantity, BigDecimal unitPrice,
                    List<BatchAllocation> batchAllocations) {
        validateParams(productId, productName, quantity, unitPrice, batchAllocations);

        this.itemId = UUID.randomUUID();
        this.productId = productId;
        this.productName = productName;
        this.productSku = productSku != null ? productSku : "";
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.batchAllocations = Collections.unmodifiableList(batchAllocations);
    }

    private static void validateParams(UUID productId, String productName, int quantity,
                                        BigDecimal unitPrice, List<BatchAllocation> batchAllocations) {
        if (productId == null) throw new IllegalArgumentException("productId must not be null");
        if (productName == null || productName.isBlank())
            throw new IllegalArgumentException("productName must not be blank");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("unitPrice must not be null or negative");
        if (batchAllocations == null || batchAllocations.isEmpty())
            throw new IllegalArgumentException("batchAllocations must not be null or empty");
    }

    public UUID getItemId() { return itemId; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getProductSku() { return productSku; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public List<BatchAllocation> getBatchAllocations() { return batchAllocations; }

    public int getAllocatedQuantitySum() {
        return batchAllocations.stream().mapToInt(BatchAllocation::allocatedQuantity).sum();
    }
}
