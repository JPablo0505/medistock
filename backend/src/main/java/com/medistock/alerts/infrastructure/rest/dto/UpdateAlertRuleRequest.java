package com.medistock.alerts.infrastructure.rest.dto;

import java.util.Map;
import java.util.UUID;

public record UpdateAlertRuleRequest(
        String name,
        String description,
        String type,
        Map<String, Object> config
) {}
