package com.medistock.alerts.domain.model;

import com.medistock.alerts.domain.exception.AlertDomainException;

import java.time.LocalDateTime;
import java.util.UUID;

public class AlertInstance {

    private UUID id;
    private UUID alertRuleId;
    private UUID productId;
    private UUID batchId;
    private AlertSeverity severity;
    private String message;
    private AlertInstanceStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;

    public AlertInstance(UUID alertRuleId, UUID productId, UUID batchId,
                         AlertSeverity severity, String message) {
        this.id = UUID.randomUUID();
        this.alertRuleId = alertRuleId;
        this.productId = productId;
        this.batchId = batchId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        setSeverity(severity);
        setMessage(message);
        this.status = AlertInstanceStatus.ACTIVE;
    }

    // Restore constructor for persistence reconstruction
    private AlertInstance(UUID id, UUID alertRuleId, UUID productId, UUID batchId,
                          AlertSeverity severity, String message,
                          AlertInstanceStatus status, LocalDateTime createdAt,
                          LocalDateTime updatedAt, LocalDateTime acknowledgedAt,
                          LocalDateTime resolvedAt) {
        this.id = id;
        this.alertRuleId = alertRuleId;
        this.productId = productId;
        this.batchId = batchId;
        this.severity = severity;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.acknowledgedAt = acknowledgedAt;
        this.resolvedAt = resolvedAt;
    }

    public static AlertInstance restore(UUID id, UUID alertRuleId, UUID productId,
                                         UUID batchId, AlertSeverity severity,
                                         String message, AlertInstanceStatus status,
                                         LocalDateTime createdAt, LocalDateTime updatedAt,
                                         LocalDateTime acknowledgedAt,
                                         LocalDateTime resolvedAt) {
        return new AlertInstance(id, alertRuleId, productId, batchId, severity,
                message, status, createdAt, updatedAt, acknowledgedAt, resolvedAt);
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getAlertRuleId() { return alertRuleId; }
    public UUID getProductId() { return productId; }
    public UUID getBatchId() { return batchId; }
    public AlertSeverity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public AlertInstanceStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }

    // --- Behaviour ---

    public void setSeverity(AlertSeverity severity) {
        if (severity == null) throw new AlertDomainException("Alert severity must not be null");
        this.severity = severity;
    }

    public void setMessage(String message) {
        if (message == null || message.isBlank())
            throw new AlertDomainException("Alert message must not be blank");
        this.message = message.trim();
    }

    public void acknowledge() {
        if (status == AlertInstanceStatus.RESOLVED)
            throw new AlertDomainException("Cannot acknowledge a resolved alert");
        if (status == AlertInstanceStatus.ACKNOWLEDGED) return;
        this.status = AlertInstanceStatus.ACKNOWLEDGED;
        this.acknowledgedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void resolve() {
        if (status == AlertInstanceStatus.RESOLVED) return;
        this.status = AlertInstanceStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void reactivate() {
        this.status = AlertInstanceStatus.ACTIVE;
        this.acknowledgedAt = null;
        this.resolvedAt = null;
        this.updatedAt = LocalDateTime.now();
    }
}
