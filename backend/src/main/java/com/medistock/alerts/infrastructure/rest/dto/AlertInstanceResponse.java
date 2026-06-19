package com.medistock.alerts.infrastructure.rest.dto;

import com.medistock.alerts.domain.model.AlertInstance;

import java.time.LocalDateTime;
import java.util.UUID;

public record AlertInstanceResponse(
        UUID id,
        UUID alertRuleId,
        UUID productId,
        UUID batchId,
        String severity,
        String message,
        String status,
        LocalDateTime createdAt,
        LocalDateTime acknowledgedAt,
        LocalDateTime resolvedAt
) {
    public static AlertInstanceResponse from(AlertInstance instance) {
        return new AlertInstanceResponse(
                instance.getId(),
                instance.getAlertRuleId(),
                instance.getProductId(),
                instance.getBatchId(),
                instance.getSeverity().name(),
                instance.getMessage(),
                instance.getStatus().name(),
                instance.getCreatedAt(),
                instance.getAcknowledgedAt(),
                instance.getResolvedAt()
        );
    }
}
