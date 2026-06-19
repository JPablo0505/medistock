package com.medistock.alerts.domain;

import com.medistock.alerts.domain.exception.AlertDomainException;
import com.medistock.alerts.domain.exception.InvalidAlertRuleException;
import com.medistock.alerts.domain.model.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AlertRuleTest {

    private final String tenantId = "tenant-1";

    @Test
    void shouldCreateActiveRule() {
        var rule = new AlertRule(tenantId, "Test Rule", "Desc",
                AlertRuleType.EXPIRATION, Map.of("expirationDays", 30));
        assertThat(rule.getName()).isEqualTo("Test Rule");
        assertThat(rule.isActive()).isTrue();
        assertThat(rule.getId()).isNotNull();
        assertThat(rule.getType()).isEqualTo(AlertRuleType.EXPIRATION);
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> new AlertRule(tenantId, "", "Desc",
                AlertRuleType.LOW_STOCK, Map.of("minStock", 10)))
                .isInstanceOf(InvalidAlertRuleException.class);
    }

    @Test
    void shouldRejectNullType() {
        assertThatThrownBy(() -> new AlertRule(tenantId, "Test", "Desc",
                null, Map.of("minStock", 10)))
                .isInstanceOf(InvalidAlertRuleException.class);
    }

    @Test
    void shouldRejectNullConfig() {
        assertThatThrownBy(() -> new AlertRule(tenantId, "Test", "Desc",
                AlertRuleType.LOW_STOCK, null))
                .isInstanceOf(InvalidAlertRuleException.class);
    }

    @Test
    void shouldToggleActive() {
        var rule = new AlertRule(tenantId, "Test", "Desc",
                AlertRuleType.EXPIRATION, Map.of("expirationDays", 30));
        assertThat(rule.isActive()).isTrue();
        rule.deactivate();
        assertThat(rule.isActive()).isFalse();
        rule.activate();
        assertThat(rule.isActive()).isTrue();
    }

    @Test
    void shouldDefaultExpirationDays() {
        var rule = new AlertRule(tenantId, "Test", "Desc",
                AlertRuleType.EXPIRATION, Map.of("severity", "high"));
        assertThat(rule.getExpirationDays()).isEqualTo(30);
    }

    @Test
    void shouldGetConfigValues() {
        var rule = new AlertRule(tenantId, "Test", "Desc",
                AlertRuleType.LOW_STOCK, Map.of("minStock", 5, "severity", "critical"));
        assertThat(rule.getMinStock()).isEqualTo(5);
        assertThat(rule.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void shouldRestoreFromPersistence() {
        UUID id = UUID.randomUUID();
        var now = java.time.LocalDateTime.now();
        var rule = AlertRule.restore(id, tenantId, "Restored", "Desc",
                AlertRuleType.LOW_STOCK, Map.of("minStock", 10), false, now, now);
        assertThat(rule.getId()).isEqualTo(id);
        assertThat(rule.getTenantId()).isEqualTo(tenantId);
        assertThat(rule.isActive()).isFalse();
        assertThat(rule.getCreatedAt()).isEqualTo(now);
    }
}
