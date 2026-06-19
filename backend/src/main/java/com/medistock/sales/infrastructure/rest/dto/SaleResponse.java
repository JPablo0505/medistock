package com.medistock.sales.infrastructure.rest.dto;

import com.medistock.sales.domain.model.Sale;
import com.medistock.sales.domain.model.SaleItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SaleResponse(
        UUID id,
        LocalDateTime saleDate,
        String saleType,
        List<SaleItemResponse> items,
        BigDecimal subtotal,
        BigDecimal total,
        String status,
        String performedBy,
        LocalDateTime voidedAt,
        String voidReason,
        LocalDateTime createdAt
) {
    public static SaleResponse from(Sale sale) {
        return new SaleResponse(
                sale.getSaleId(),
                sale.getSaleDate(),
                sale.getSaleType().name(),
                sale.getItems().stream().map(SaleItemResponse::from).toList(),
                sale.getSubtotal(),
                sale.getTotal(),
                sale.getStatus().name(),
                sale.getPerformedBy(),
                sale.getVoidedAt(),
                sale.getVoidReason(),
                sale.getCreatedAt()
        );
    }

    public record SaleItemResponse(
            UUID itemId,
            UUID productId,
            String productName,
            String productSku,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            List<BatchAllocationResponse> batchAllocations
    ) {
        public static SaleItemResponse from(SaleItem item) {
            return new SaleItemResponse(
                    item.getItemId(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getProductSku(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getTotalPrice(),
                    item.getBatchAllocations().stream()
                            .map(BatchAllocationResponse::from).toList()
            );
        }
    }

    public record BatchAllocationResponse(
            UUID batchId,
            String lotNumber,
            String expirationDate,
            int allocatedQuantity,
            BigDecimal costPerUnit
    ) {
        public static BatchAllocationResponse from(
                com.medistock.sales.domain.model.BatchAllocation alloc) {
            return new BatchAllocationResponse(
                    alloc.batchId(),
                    alloc.lotNumber(),
                    alloc.expirationDate() != null ? alloc.expirationDate().toString() : null,
                    alloc.allocatedQuantity(),
                    alloc.costPerUnit()
            );
        }
    }
}
