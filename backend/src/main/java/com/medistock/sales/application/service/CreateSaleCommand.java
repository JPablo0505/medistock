package com.medistock.sales.application.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateSaleCommand(
        String tenantId,
        String performedBy,
        List<CreateSaleItem> items
) {
    public record CreateSaleItem(
            UUID productId,
            String productName,
            String productSku,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
