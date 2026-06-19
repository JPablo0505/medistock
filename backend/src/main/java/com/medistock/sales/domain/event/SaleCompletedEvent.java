package com.medistock.sales.domain.event;

import com.medistock.shared.domain.DomainEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SaleCompletedEvent(
        UUID saleId,
        String tenantId,
        BigDecimal total,
        String performedBy,
        LocalDateTime timestamp
) implements DomainEvent {
    public SaleCompletedEvent(UUID saleId, String tenantId, BigDecimal total, String performedBy) {
        this(saleId, tenantId, total, performedBy, LocalDateTime.now());
    }
}
