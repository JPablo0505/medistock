package com.medistock.inventory.infrastructure.rest;

import com.medistock.inventory.domain.model.Movement;
import java.time.LocalDateTime;
import java.util.UUID;

public record MovementResponse(
        UUID id,
        UUID batchId,
        UUID productId,
        String type,
        int quantity,
        int previousQuantity,
        int newQuantity,
        String reason,
        String performedBy,
        LocalDateTime createdAt
) {
    public static MovementResponse fromDomain(Movement movement) {
        return new MovementResponse(
                movement.getId(), movement.getBatchId(), movement.getProductId(),
                movement.getType().name(), movement.getQuantity(),
                movement.getPreviousQuantity(), movement.getNewQuantity(),
                movement.getReason(), movement.getPerformedBy(), movement.getCreatedAt()
        );
    }
}
