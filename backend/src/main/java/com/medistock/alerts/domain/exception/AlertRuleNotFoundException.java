package com.medistock.alerts.domain.exception;

import java.util.UUID;

public class AlertRuleNotFoundException extends AlertDomainException {
    public AlertRuleNotFoundException(UUID id) {
        super("Alert rule not found: " + id);
    }
}
