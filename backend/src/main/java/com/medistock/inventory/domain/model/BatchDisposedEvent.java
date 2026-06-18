package com.medistock.inventory.domain.model;

import com.medistock.shared.domain.DomainEvent;
import java.util.UUID;

public record BatchDisposedEvent(UUID batchId, UUID productId, String reason,
                                 int quantity, String tenantId) implements DomainEvent {}
