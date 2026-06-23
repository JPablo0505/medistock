package com.medistock.alerts.domain.model;

import com.medistock.alerts.domain.exception.InvalidAlertRuleException;
import com.medistock.shared.domain.AggregateRoot;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class AlertRule implements AggregateRoot {

    private UUID id;
    private String tenantId;
    private String name;
    private String description;
    private AlertRuleType type;
    private Map<String, Object> config;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Default config keys per type
    public static final String CFG_EXPIRATION_DAYS = "expirationDays";
    public static final String CFG_MIN_STOCK = "minStock";
    public static final String CFG_SEVERITY = "severity";

    public AlertRule(String tenantId, String name, String description, AlertRuleType type,
                     Map<String, Object> config) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.isActive = true;
        this.createdAt = LocalDateTime.now(Clock.systemDefaultZone());
        this.updatedAt = this.createdAt;
        setName(name);
        setDescription(description);
        setType(type);
        setConfig(config);
    }

    // Restore constructor for persistence reconstruction
    private AlertRule(UUID id, String tenantId, String name, String description, AlertRuleType type,
                      Map<String, Object> config, boolean isActive,
                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.type = type;
        this.config = config;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @SuppressWarnings("java:S107")
    public static AlertRule restore(UUID id, String tenantId, String name, String description,
                                      AlertRuleType type, Map<String, Object> config,
                                      boolean isActive, LocalDateTime createdAt,
                                      LocalDateTime updatedAt) {
        return new AlertRule(id, tenantId, name, description, type, config, isActive, createdAt, updatedAt);
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public AlertRuleType getType() { return type; }
    public Map<String, Object> getConfig() { return config; }
    public boolean isActive() { return isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // --- Behaviour ---

    public void activate() { this.isActive = true; updatedAt = LocalDateTime.now(Clock.systemDefaultZone()); }
    public void deactivate() { this.isActive = false; updatedAt = LocalDateTime.now(Clock.systemDefaultZone()); }

    public void setName(String name) {
        if (name == null || name.isBlank())
            throw new InvalidAlertRuleException("Alert rule name must not be blank");
        this.name = name.trim();
        updatedAt = LocalDateTime.now(Clock.systemDefaultZone());
    }

    public void setDescription(String description) {
        this.description = description;
        updatedAt = LocalDateTime.now(Clock.systemDefaultZone());
    }

    public void setType(AlertRuleType type) {
        if (type == null)
            throw new InvalidAlertRuleException("Alert rule type must not be null");
        this.type = type;
        updatedAt = LocalDateTime.now(Clock.systemDefaultZone());
    }

    public void setConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty())
            throw new InvalidAlertRuleException("Alert rule config must not be null or empty");
        this.config = Map.copyOf(config);
        updatedAt = LocalDateTime.now(Clock.systemDefaultZone());
    }

    public int getExpirationDays() {
        Object val = config.get(CFG_EXPIRATION_DAYS);
        if (val instanceof Number n) return n.intValue();
        return 30;
    }

    public int getMinStock() {
        Object val = config.get(CFG_MIN_STOCK);
        if (val instanceof Number n) return n.intValue();
        return 10;
    }

    public AlertSeverity getSeverity() {
        Object val = config.get(CFG_SEVERITY);
        if (val instanceof String s) {
            try { return AlertSeverity.valueOf(s.toUpperCase()); }
            catch (IllegalArgumentException e) { return AlertSeverity.MEDIUM; }
        }
        return AlertSeverity.MEDIUM;
    }
}
