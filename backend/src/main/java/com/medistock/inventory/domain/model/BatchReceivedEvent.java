package com.medistock.inventory.domain.model;

import com.medistock.shared.domain.DomainEvent;
import java.time.LocalDate;
import java.util.UUID;

public record BatchReceivedEvent(UUID batchId, UUID productId, int quantity,
                                 LocalDate expiryDate, String tenantId) implements DomainEvent {}
