package com.medistock.alerts.domain.event;

import com.medistock.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record AlertTriggeredEvent(
        UUID eventId,
        UUID alertRuleId,
        UUID productId,
        UUID batchId,
        String message,
        String severity,
        Instant occurredOn
) implements DomainEvent {

    public AlertTriggeredEvent(UUID alertRuleId, UUID productId, UUID batchId,
                               String message, String severity) {
        this(UUID.randomUUID(), alertRuleId, productId, batchId,
                message, severity, Instant.now());
    }

    @Override
    public UUID eventId() {
        return eventId;
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }
}
