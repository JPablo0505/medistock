package com.medistock.alerts.infrastructure.rest.dto;

import com.medistock.alerts.domain.model.AlertRule;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AlertRuleResponse(
        UUID id,
        String name,
        String description,
        String type,
        Map<String, Object> config,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AlertRuleResponse from(AlertRule rule) {
        return new AlertRuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getDescription(),
                rule.getType().name(),
                rule.getConfig(),
                rule.isActive(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
