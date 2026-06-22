package com.medistock.alerts.domain;

import com.medistock.alerts.domain.exception.AlertDomainException;
import com.medistock.alerts.domain.model.*;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

import com.medistock.alerts.domain.model.AlertSeverity;

class AlertInstanceTest {

    @Test
    void shouldCreateActiveInstance() {
        var instance = new AlertInstance(
                "default", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                AlertSeverity.HIGH, "Test alert");
        assertThat(instance.getStatus()).isEqualTo(AlertInstanceStatus.ACTIVE);
        assertThat(instance.getMessage()).isEqualTo("Test alert");
        assertThat(instance.getId()).isNotNull();
    }

    @Test
    void shouldRejectBlankMessage() {
        assertThatThrownBy(() -> createInvalid("default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.LOW, ""))
                .isInstanceOf(AlertDomainException.class);
    }

    @Test
    void shouldRejectNullSeverity() {
        assertThatThrownBy(() -> createInvalid("default", UUID.randomUUID(), UUID.randomUUID(), null,
                null, "msg"))
                .isInstanceOf(AlertDomainException.class);
    }

    private AlertInstance createInvalid(String tenantId, UUID ruleId, UUID productId, UUID batchId,
                                         AlertSeverity severity, String message) {
        return new AlertInstance(tenantId, ruleId, productId, batchId, severity, message);
    }

    @Test
    void shouldAcknowledge() {
        var instance = new AlertInstance(
                "default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.MEDIUM, "msg");
        instance.acknowledge();
        assertThat(instance.getStatus()).isEqualTo(AlertInstanceStatus.ACKNOWLEDGED);
        assertThat(instance.getAcknowledgedAt()).isNotNull();
    }

    @Test
    void shouldBeIdempotentOnAcknowledge() {
        var instance = new AlertInstance(
                "default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.LOW, "msg");
        instance.acknowledge();
        var ackTime = instance.getAcknowledgedAt();
        instance.acknowledge();
        assertThat(instance.getAcknowledgedAt()).isEqualTo(ackTime);
    }

    @Test
    void shouldResolve() {
        var instance = new AlertInstance(
                "default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.LOW, "msg");
        instance.resolve();
        assertThat(instance.getStatus()).isEqualTo(AlertInstanceStatus.RESOLVED);
        assertThat(instance.getResolvedAt()).isNotNull();
    }

    @Test
    void shouldNotAcknowledgeWhenResolved() {
        var instance = new AlertInstance(
                "default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.LOW, "msg");
        instance.resolve();
        assertThatThrownBy(instance::acknowledge)
                .isInstanceOf(AlertDomainException.class);
    }

    @Test
    void shouldReactivate() {
        var instance = new AlertInstance(
                "default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.LOW, "msg");
        instance.acknowledge();
        instance.reactivate();
        assertThat(instance.getStatus()).isEqualTo(AlertInstanceStatus.ACTIVE);
        assertThat(instance.getAcknowledgedAt()).isNull();
    }

    @Test
    void shouldRestoreFromPersistence() {
        UUID id = UUID.randomUUID();
        var now = java.time.LocalDateTime.now();
        var instance = AlertInstance.restore(id, "default", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), AlertSeverity.CRITICAL, "msg",
                AlertInstanceStatus.ACKNOWLEDGED, now, now, now, null);
        assertThat(instance.getId()).isEqualTo(id);
        assertThat(instance.getStatus()).isEqualTo(AlertInstanceStatus.ACKNOWLEDGED);
        assertThat(instance.getAcknowledgedAt()).isEqualTo(now);
    }
}
