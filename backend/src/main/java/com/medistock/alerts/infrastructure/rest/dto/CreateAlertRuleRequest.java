package com.medistock.alerts.infrastructure.rest.dto;

import java.util.Map;

public record CreateAlertRuleRequest(
        String name,
        String description,
        String type,
        Map<String, Object> config
) {}
