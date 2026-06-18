package com.medistock.inventory.domain.model;

import com.medistock.shared.domain.DomainEvent;
import java.util.UUID;

public record StockAdjustedEvent(UUID batchId, UUID productId, int previousQuantity,
                                 int newQuantity, String reason, String tenantId) implements DomainEvent {}
