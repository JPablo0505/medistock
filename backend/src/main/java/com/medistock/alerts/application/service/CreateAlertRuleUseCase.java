package com.medistock.alerts.application.service;

import com.medistock.alerts.domain.model.AlertRule;
import com.medistock.alerts.domain.model.AlertRuleType;
import com.medistock.alerts.domain.port.AlertRuleRepository;
import com.medistock.alerts.domain.exception.InvalidAlertRuleException;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;

@Service
public class CreateAlertRuleUseCase {

    private final AlertRuleRepository alertRuleRepository;

    public CreateAlertRuleUseCase(AlertRuleRepository alertRuleRepository) {
        this.alertRuleRepository = alertRuleRepository;
    }

    public AlertRule execute(String tenantId, String name, String description, String type,
                             Map<String, Object> config) {
        AlertRuleType ruleType;
        try {
            ruleType = AlertRuleType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidAlertRuleException("Invalid alert rule type: " + type);
        }

        AlertRule rule = new AlertRule(tenantId, name, description, ruleType, config);
        return alertRuleRepository.save(rule);
    }
}
