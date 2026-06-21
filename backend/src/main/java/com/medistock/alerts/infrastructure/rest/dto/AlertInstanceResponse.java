package com.medistock.alerts.infrastructure.rest.dto;

import com.medistock.alerts.domain.model.AlertInstance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AlertInstanceResponse(
        UUID id,
        UUID alertRuleId,
        UUID productId,
        String productName,
        String productDosageForm,
        String productManufacturer,
        Integer productStock,
        LocalDate productExpirationDate,
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
                null,   // productName — filled by service layer
                null,   // productDosageForm
                null,   // productManufacturer
                null,   // productStock
                null,   // productExpirationDate
                instance.getBatchId(),
                instance.getSeverity().name(),
                instance.getMessage(),
                instance.getStatus().name(),
                instance.getCreatedAt(),
                instance.getAcknowledgedAt(),
                instance.getResolvedAt()
        );
    }

    // Factory with product enrichment data
    public record ProductEnrichment(
            String name,
            String dosageForm,
            String manufacturer,
            int stock,
            LocalDate expirationDate
    ) {}

    public static AlertInstanceResponse from(AlertInstance instance, ProductEnrichment product) {
        return new AlertInstanceResponse(
                instance.getId(),
                instance.getAlertRuleId(),
                instance.getProductId(),
                product != null ? product.name() : null,
                product != null ? product.dosageForm() : null,
                product != null ? product.manufacturer() : null,
                product != null ? product.stock() : null,
                product != null ? product.expirationDate() : null,
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
