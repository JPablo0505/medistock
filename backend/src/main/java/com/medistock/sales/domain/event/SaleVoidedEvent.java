package com.medistock.sales.domain.event;

import com.medistock.shared.domain.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

public record SaleVoidedEvent(
        UUID saleId,
        String tenantId,
        String reason,
        LocalDateTime timestamp
) implements DomainEvent {
    public SaleVoidedEvent(UUID saleId, String tenantId, String reason) {
        this(saleId, tenantId, reason, LocalDateTime.now());
    }
}
