package com.medistock.alerts.domain;

import com.medistock.alerts.domain.exception.InvalidAlertRuleException;
import com.medistock.alerts.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AlertRule domain entity")
@SuppressWarnings("java:S5778")
class AlertRuleTest {

    private final String tenantId = "tenant-1";

    @Test
    @DisplayName("Should create active rule with valid parameters")
    void shouldCreateActiveRule() {
        // Arrange
        var rule = new AlertRule(tenantId, "Test Rule", "Desc",
                AlertRuleType.EXPIRATION, Map.of("expirationDays", 30));

        // Assert
        assertThat(rule.getName()).as("rule name").isEqualTo("Test Rule");
        assertThat(rule.isActive()).as("active flag").isTrue();
        assertThat(rule.getId()).as("rule id").isNotNull();
        assertThat(rule.getType()).as("rule type").isEqualTo(AlertRuleType.EXPIRATION);
    }

    @Test
    @DisplayName("Should reject blank name when creating alert rule")
    void shouldRejectBlankName() {
        // Act & Assert
        assertThatThrownBy(() -> new AlertRule(tenantId, "", "Desc",
                AlertRuleType.LOW_STOCK, Map.of("minStock", 10)))
                .as("Blank name should throw InvalidAlertRuleException")
                .isInstanceOf(InvalidAlertRuleException.class);
    }

    @Test
    @DisplayName("Should reject null type when creating alert rule")
    void shouldRejectNullType() {
        // Act & Assert
        assertThatThrownBy(() -> new AlertRule(tenantId, "Test", "Desc",
                null, Map.of("minStock", 10)))
                .as("Null type should throw InvalidAlertRuleException")
                .isInstanceOf(InvalidAlertRuleException.class);
    }

    @Test
    @DisplayName("Should reject null config when creating alert rule")
    void shouldRejectNullConfig() {
        // Act & Assert
        assertThatThrownBy(() -> new AlertRule(tenantId, "Test", "Desc",
                AlertRuleType.LOW_STOCK, null))
                .as("Null config should throw InvalidAlertRuleException")
                .isInstanceOf(InvalidAlertRuleException.class);
    }

    @Test
    @DisplayName("Should toggle active state on deactivate and activate")
    void shouldToggleActive() {
        // Arrange
        var rule = new AlertRule(tenantId, "Test", "Desc",
                AlertRuleType.EXPIRATION, Map.of("expirationDays", 30));

        // Act & Assert
        assertThat(rule.isActive()).as("initial active state").isTrue();
        rule.deactivate();
        assertThat(rule.isActive()).as("after deactivate").isFalse();
        rule.activate();
        assertThat(rule.isActive()).as("after reactivate").isTrue();
    }

    @Test
    @DisplayName("Should default expiration days to 30 when not configured")
    void shouldDefaultExpirationDays() {
        // Arrange
        var rule = new AlertRule(tenantId, "Test", "Desc",
                AlertRuleType.EXPIRATION, Map.of("severity", "high"));

        // Assert
        assertThat(rule.getExpirationDays()).as("default expiration days").isEqualTo(30);
    }

    @Test
    @DisplayName("Should get config values for min stock and severity")
    void shouldGetConfigValues() {
        // Arrange
        var rule = new AlertRule(tenantId, "Test", "Desc",
                AlertRuleType.LOW_STOCK, Map.of("minStock", 5, "severity", "critical"));

        // Assert
        assertThat(rule.getMinStock()).as("min stock config").isEqualTo(5);
        assertThat(rule.getSeverity()).as("severity config").isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    @DisplayName("Should restore alert rule from persistence data")
    void shouldRestoreFromPersistence() {
        // Arrange
        UUID id = UUID.randomUUID();
        var now = java.time.LocalDateTime.now();

        // Act
        var rule = AlertRule.restore(id, tenantId, "Restored", "Desc",
                AlertRuleType.LOW_STOCK, Map.of("minStock", 10), false, now, now);

        // Assert
        assertThat(rule.getId()).as("restored id").isEqualTo(id);
        assertThat(rule.getTenantId()).as("tenant id").isEqualTo(tenantId);
        assertThat(rule.isActive()).as("active flag").isFalse();
        assertThat(rule.getCreatedAt()).as("created timestamp").isEqualTo(now);
    }
}
