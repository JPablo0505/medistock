package com.medistock.sales.infrastructure.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateSaleRequest(
        @NotEmpty List<@Valid SaleItemRequest> items,
        String notes
) {
    public record SaleItemRequest(
            UUID productId,
            String productName,
            String productSku,
            @Positive int quantity,
            BigDecimal unitPrice
    ) {}
}
