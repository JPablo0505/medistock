package com.medistock.alerts.domain.service;

import com.medistock.alerts.domain.event.AlertTriggeredEvent;
import com.medistock.alerts.domain.model.AlertRule;
import com.medistock.alerts.domain.model.AlertRuleType;

import java.util.List;

public interface AlertRuleEvaluator {

    AlertRuleType supportedType();

    List<AlertTriggeredEvent> evaluate(String tenantId, AlertRule rule);
}
