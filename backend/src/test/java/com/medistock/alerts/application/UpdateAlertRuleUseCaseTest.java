package com.medistock.alerts.application;

import com.medistock.alerts.application.service.UpdateAlertRuleUseCase;
import com.medistock.alerts.domain.exception.AlertRuleNotFoundException;
import com.medistock.alerts.domain.exception.InvalidAlertRuleException;
import com.medistock.alerts.domain.model.AlertRule;
import com.medistock.alerts.domain.model.AlertRuleType;
import com.medistock.alerts.domain.port.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateAlertRuleUseCase")
class UpdateAlertRuleUseCaseTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    private UpdateAlertRuleUseCase useCase;

    private AlertRule existingRule;

    @BeforeEach
    void setUp() {
        useCase = new UpdateAlertRuleUseCase(alertRuleRepository);
        existingRule = new AlertRule("tenant-1", "Original Name", "Original Desc",
                AlertRuleType.EXPIRATION, Map.of("expirationDays", 30));
    }

    @Test
    @DisplayName("Should update rule name while preserving description")
    void shouldUpdateName() {
        // Arrange
        when(alertRuleRepository.findById(existingRule.getId())).thenReturn(Optional.of(existingRule));
        when(alertRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute(existingRule.getId(), "New Name", null, null, null);

        // Assert
        assertThat(result.getName()).as("updated name").isEqualTo("New Name");
        assertThat(result.getDescription()).as("original description preserved").isEqualTo("Original Desc");
    }

    @Test
    @DisplayName("Should update rule description")
    void shouldUpdateDescription() {
        // Arrange
        when(alertRuleRepository.findById(existingRule.getId())).thenReturn(Optional.of(existingRule));
        when(alertRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute(existingRule.getId(), null, "New Desc", null, null);

        // Assert
        assertThat(result.getDescription()).as("updated description").isEqualTo("New Desc");
    }

    @Test
    @DisplayName("Should update rule type")
    void shouldUpdateType() {
        // Arrange
        when(alertRuleRepository.findById(existingRule.getId())).thenReturn(Optional.of(existingRule));
        when(alertRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute(existingRule.getId(), null, null, "LOW_STOCK", null);

        // Assert
        assertThat(result.getType()).as("updated type").isEqualTo(AlertRuleType.LOW_STOCK);
    }

    @Test
    @DisplayName("Should update rule config")
    void shouldUpdateConfig() {
        // Arrange
        when(alertRuleRepository.findById(existingRule.getId())).thenReturn(Optional.of(existingRule));
        when(alertRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var newConfig = Map.<String, Object>of("expirationDays", 15);

        // Act
        var result = useCase.execute(existingRule.getId(), null, null, null, newConfig);

        // Assert
        assertThat(result.getExpirationDays()).as("updated expiration days").isEqualTo(15);
    }

    @Test
    @DisplayName("Should throw on invalid rule type")
    void shouldThrowOnInvalidType() {
        // Arrange
        when(alertRuleRepository.findById(existingRule.getId())).thenReturn(Optional.of(existingRule));

        // Act & Assert
        assertThatThrownBy(() ->
                useCase.execute(existingRule.getId(), null, null, "INVALID_TYPE", null))
                .as("Invalid type should throw InvalidAlertRuleException")
                .isInstanceOf(InvalidAlertRuleException.class);

        verify(alertRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when rule not found")
    void shouldThrowWhenRuleNotFound() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(alertRuleRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() ->
                useCase.execute(id, "Name", null, null, null))
                .as("Non-existent rule should throw AlertRuleNotFoundException")
                .isInstanceOf(AlertRuleNotFoundException.class);

        verify(alertRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should toggle active rule to inactive")
    void shouldToggleActiveToInactive() {
        // Arrange
        when(alertRuleRepository.findById(existingRule.getId())).thenReturn(Optional.of(existingRule));
        when(alertRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        useCase.toggleActive(existingRule.getId());

        // Assert
        assertThat(existingRule.isActive()).as("toggled active state").isFalse();
    }

    @Test
    @DisplayName("Should toggle inactive rule to active")
    void shouldToggleInactiveToActive() {
        // Arrange
        existingRule.deactivate();
        when(alertRuleRepository.findById(existingRule.getId())).thenReturn(Optional.of(existingRule));
        when(alertRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        useCase.toggleActive(existingRule.getId());

        // Assert
        assertThat(existingRule.isActive()).as("toggled active state").isTrue();
    }

    @Test
    @DisplayName("Should throw on toggle when rule not found")
    void shouldThrowOnToggleWhenNotFound() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(alertRuleRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> useCase.toggleActive(id))
                .as("Toggle on non-existent rule should throw AlertRuleNotFoundException")
                .isInstanceOf(AlertRuleNotFoundException.class);

        verify(alertRuleRepository, never()).save(any());
    }
}
