package com.medistock.alerts.infrastructure.rest.dto;

import com.medistock.alerts.domain.model.AlertInstance;
import com.medistock.alerts.domain.model.AlertSeverity;
import com.medistock.alerts.domain.model.AlertInstanceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AlertInstanceResponse DTO")
class AlertInstanceResponseTest {

    private final UUID ruleId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID batchId = UUID.randomUUID();

    @Test
    @DisplayName("Should create from AlertInstance without product enrichment")
    void shouldCreateFromInstanceWithoutEnrichment() {
        // Arrange
        var instance = new AlertInstance("tenant", ruleId, productId, batchId,
                AlertSeverity.HIGH, "Test alert message");

        // Act
        var response = AlertInstanceResponse.from(instance);

        // Assert
        assertEquals(instance.getId(), response.id(), "id");
        assertEquals(ruleId, response.alertRuleId(), "alert rule id");
        assertEquals(productId, response.productId(), "product id");
        assertEquals(batchId, response.batchId(), "batch id");
        assertEquals("HIGH", response.severity(), "severity");
        assertEquals("Test alert message", response.message(), "message");
        assertEquals("ACTIVE", response.status(), "status");
        assertNull(response.productName(), "product name should be null");
        assertNull(response.productStock(), "product stock should be null");
    }

    @Test
    @DisplayName("Should create from AlertInstance with product enrichment")
    void shouldCreateFromInstanceWithEnrichment() {
        // Arrange
        var instance = new AlertInstance("tenant", ruleId, productId, batchId,
                AlertSeverity.LOW, "Low stock");
        var enrichment = new AlertInstanceResponse.ProductEnrichment(
                "Ibuprofeno", "Tablet", "MediCorp",
                150, LocalDate.of(2026, 12, 31));

        // Act
        var response = AlertInstanceResponse.from(instance, enrichment);

        // Assert
        assertEquals("Ibuprofeno", response.productName(), "product name");
        assertEquals("Tablet", response.productDosageForm(), "dosage form");
        assertEquals("MediCorp", response.productManufacturer(), "manufacturer");
        assertEquals(150, response.productStock(), "stock");
        assertEquals(LocalDate.of(2026, 12, 31), response.productExpirationDate(), "expiration");
        assertEquals("LOW", response.severity(), "severity");
    }

    @Test
    @DisplayName("Should create from AlertInstance with null enrichment")
    void shouldCreateFromInstanceWithNullEnrichment() {
        // Arrange
        var instance = new AlertInstance("tenant", ruleId, productId, batchId,
                AlertSeverity.MEDIUM, "Test");

        // Act
        var response = AlertInstanceResponse.from(instance, (AlertInstanceResponse.ProductEnrichment) null);

        // Assert
        assertNull(response.productName(), "product name should be null when enrichment is null");
        assertNull(response.productStock(), "product stock should be null when enrichment is null");
        assertEquals("MEDIUM", response.severity(), "severity unchanged");
    }
}
