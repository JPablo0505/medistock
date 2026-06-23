package com.medistock.alerts.domain;

import com.medistock.alerts.domain.exception.AlertDomainException;
import com.medistock.alerts.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AlertInstance domain entity")
class AlertInstanceTest {

    private AlertInstance createDefaultInstance() {
        return new AlertInstance(
                "default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.LOW, "msg");
    }

    @Test
    @DisplayName("Should create active instance with provided values")
    void shouldCreateActiveInstance() {
        // Arrange
        var instance = new AlertInstance(
                "default", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                AlertSeverity.HIGH, "Test alert");

        // Assert
        assertThat(instance.getStatus()).as("initial status").isEqualTo(AlertInstanceStatus.ACTIVE);
        assertThat(instance.getMessage()).as("message").isEqualTo("Test alert");
        assertThat(instance.getId()).as("id").isNotNull();
    }

    @Test
    @DisplayName("Should reject blank message when creating alert instance")
    void shouldRejectBlankMessage() {
        // Arrange
        String blankMessage = "";

        // Act & Assert
        assertThatThrownBy(() -> new AlertInstance("default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.LOW, blankMessage))
                .as("Blank message should throw AlertDomainException")
                .isInstanceOf(AlertDomainException.class);
    }

    @Test
    @DisplayName("Should reject null severity when creating alert instance")
    void shouldRejectNullSeverity() {
        // Arrange
        AlertSeverity nullSeverity = null;

        // Act & Assert
        assertThatThrownBy(() -> new AlertInstance("default", UUID.randomUUID(), UUID.randomUUID(), null,
                nullSeverity, "msg"))
                .as("Null severity should throw AlertDomainException")
                .isInstanceOf(AlertDomainException.class);
    }

    @Test
    @DisplayName("Should acknowledge active alert instance")
    void shouldAcknowledge() {
        // Arrange
        var instance = new AlertInstance(
                "default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.MEDIUM, "msg");

        // Act
        instance.acknowledge();

        // Assert
        assertThat(instance.getStatus()).as("status after acknowledge").isEqualTo(AlertInstanceStatus.ACKNOWLEDGED);
        assertThat(instance.getAcknowledgedAt()).as("acknowledged timestamp").isNotNull();
    }

    @Test
    @DisplayName("Should be idempotent when acknowledging already acknowledged instance")
    void shouldBeIdempotentOnAcknowledge() {
        // Arrange
        var instance = new AlertInstance(
                "default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.LOW, "msg");
        instance.acknowledge();
        var ackTime = instance.getAcknowledgedAt();

        // Act
        instance.acknowledge();

        // Assert
        assertThat(instance.getAcknowledgedAt()).as("acknowledged timestamp after second acknowledge").isEqualTo(ackTime);
    }

    @Test
    @DisplayName("Should resolve active alert instance")
    void shouldResolve() {
        // Arrange
        var instance = new AlertInstance(
                "default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.LOW, "msg");

        // Act
        instance.resolve();

        // Assert
        assertThat(instance.getStatus()).as("status after resolve").isEqualTo(AlertInstanceStatus.RESOLVED);
        assertThat(instance.getResolvedAt()).as("resolved timestamp").isNotNull();
    }

    @Test
    @DisplayName("Should not acknowledge already resolved alert instance")
    void shouldNotAcknowledgeWhenResolved() {
        // Arrange
        var instance = new AlertInstance(
                "default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.LOW, "msg");
        instance.resolve();

        // Act & Assert
        assertThatThrownBy(instance::acknowledge)
                .as("Acknowledging resolved instance should throw AlertDomainException")
                .isInstanceOf(AlertDomainException.class);
    }

    @Test
    @DisplayName("Should reactivate acknowledged alert instance")
    void shouldReactivate() {
        // Arrange
        var instance = new AlertInstance(
                "default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.LOW, "msg");
        instance.acknowledge();

        // Act
        instance.reactivate();

        // Assert
        assertThat(instance.getStatus()).as("status after reactivate").isEqualTo(AlertInstanceStatus.ACTIVE);
        assertThat(instance.getAcknowledgedAt()).as("acknowledged timestamp after reactivate").isNull();
    }

    @Test
    @DisplayName("Should restore alert instance from persistence data")
    void shouldRestoreFromPersistence() {
        // Arrange
        UUID id = UUID.randomUUID();
        var now = java.time.LocalDateTime.now();

        // Act
        var instance = AlertInstance.restore(id, "default", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), AlertSeverity.CRITICAL, "msg",
                AlertInstanceStatus.ACKNOWLEDGED, now, now, now, null);

        // Assert
        assertThat(instance.getId()).as("restored id").isEqualTo(id);
        assertThat(instance.getStatus()).as("restored status").isEqualTo(AlertInstanceStatus.ACKNOWLEDGED);
        assertThat(instance.getAcknowledgedAt()).as("restored acknowledged timestamp").isEqualTo(now);
    }
}
