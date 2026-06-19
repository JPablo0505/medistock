package com.medistock.sales.infrastructure.rest.dto;

import com.medistock.sales.domain.model.Sale;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SaleSummaryResponse(
        UUID id,
        LocalDateTime saleDate,
        int itemCount,
        BigDecimal total,
        String status,
        String performedBy,
        LocalDateTime createdAt
) {
    public static SaleSummaryResponse from(Sale sale) {
        return new SaleSummaryResponse(
                sale.getSaleId(),
                sale.getSaleDate(),
                sale.getItemCount(),
                sale.getTotal(),
                sale.getStatus().name(),
                sale.getPerformedBy(),
                sale.getCreatedAt()
        );
    }
}
