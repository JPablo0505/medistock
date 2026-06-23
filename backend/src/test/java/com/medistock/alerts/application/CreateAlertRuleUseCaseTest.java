package com.medistock.alerts.application;

import com.medistock.alerts.application.service.CreateAlertRuleUseCase;
import com.medistock.alerts.domain.exception.InvalidAlertRuleException;
import com.medistock.alerts.domain.model.AlertRuleType;
import com.medistock.alerts.domain.port.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateAlertRuleUseCase")
@SuppressWarnings("java:S5778")
class CreateAlertRuleUseCaseTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    private CreateAlertRuleUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateAlertRuleUseCase(alertRuleRepository);
    }

    @Test
    @DisplayName("Should create alert rule with valid params")
    void shouldCreateRule() {
        // Arrange
        var config = Map.<String, Object>of("expirationDays", 30);
        when(alertRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute("tenant-1", "Exp Alert", "Desc", "EXPIRATION", config);

        // Assert
        assertThat(result.getName()).as("rule name").isEqualTo("Exp Alert");
        assertThat(result.getType()).as("rule type").isEqualTo(AlertRuleType.EXPIRATION);
        verify(alertRuleRepository).save(any());
    }

    @Test
    @DisplayName("Should throw on invalid rule type")
    void shouldThrowOnInvalidType() {
        // Act & Assert
        assertThatThrownBy(() ->
                useCase.execute("t", "Test", "Desc", "INVALID", Map.<String, Object>of("k", "v")))
                .as("Invalid type should throw InvalidAlertRuleException")
                .isInstanceOf(InvalidAlertRuleException.class);
        verify(alertRuleRepository, never()).save(any());
    }
}
