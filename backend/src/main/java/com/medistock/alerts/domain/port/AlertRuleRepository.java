package com.medistock.alerts.domain.port;

import com.medistock.alerts.domain.model.AlertRule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRuleRepository {
    AlertRule save(AlertRule rule);
    Optional<AlertRule> findById(UUID id);
    List<AlertRule> findAll(String tenantId);
    List<AlertRule> findByType(String tenantId, String type);
    List<AlertRule> findActiveByType(String tenantId, String type);
    void deleteById(UUID id);
}
