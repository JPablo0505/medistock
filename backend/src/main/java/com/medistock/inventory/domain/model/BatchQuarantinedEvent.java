package com.medistock.inventory.domain.model;

import com.medistock.shared.domain.DomainEvent;
import java.util.UUID;

public record BatchQuarantinedEvent(UUID batchId, UUID productId, String reason,
                                    String tenantId) implements DomainEvent {}
