package com.medistock.alerts.domain.exception;

import java.util.UUID;

public class AlertInstanceNotFoundException extends AlertDomainException {
    public AlertInstanceNotFoundException(UUID id) {
        super("Alert instance not found: " + id);
    }
}
