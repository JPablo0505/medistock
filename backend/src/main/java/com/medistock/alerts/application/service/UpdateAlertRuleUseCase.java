package com.medistock.alerts.application.service;

import com.medistock.alerts.domain.exception.AlertRuleNotFoundException;
import com.medistock.alerts.domain.model.AlertRule;
import com.medistock.alerts.domain.model.AlertRuleType;
import com.medistock.alerts.domain.port.AlertRuleRepository;
import com.medistock.alerts.domain.exception.InvalidAlertRuleException;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;

@Service
public class UpdateAlertRuleUseCase {

    private final AlertRuleRepository alertRuleRepository;

    public UpdateAlertRuleUseCase(AlertRuleRepository alertRuleRepository) {
        this.alertRuleRepository = alertRuleRepository;
    }

    public AlertRule execute(UUID ruleId, String name, String description,
                             String type, Map<String, Object> config) {
        AlertRule rule = alertRuleRepository.findById(ruleId)
                .orElseThrow(() -> new AlertRuleNotFoundException(ruleId));

        if (name != null) rule.setName(name);
        if (description != null) rule.setDescription(description);
        if (type != null) rule.setType(parseRuleType(type));
        if (config != null) rule.setConfig(config);

        return alertRuleRepository.save(rule);
    }

    private AlertRuleType parseRuleType(String type) {
        try { return AlertRuleType.valueOf(type.toUpperCase()); }
        catch (IllegalArgumentException e) { throw new InvalidAlertRuleException("Invalid alert rule type: " + type); }
    }

    public void toggleActive(UUID ruleId) {
        AlertRule rule = alertRuleRepository.findById(ruleId)
                .orElseThrow(() -> new AlertRuleNotFoundException(ruleId));
        if (rule.isActive()) { rule.deactivate(); } else { rule.activate(); }
        alertRuleRepository.save(rule);
    }
}
