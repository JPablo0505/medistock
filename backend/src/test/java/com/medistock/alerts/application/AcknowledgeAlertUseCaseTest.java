package com.medistock.alerts.application;

import com.medistock.alerts.application.service.AcknowledgeAlertUseCase;
import com.medistock.alerts.domain.exception.AlertInstanceNotFoundException;
import com.medistock.alerts.domain.model.*;
import com.medistock.alerts.domain.port.AlertInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcknowledgeAlertUseCase")
class AcknowledgeAlertUseCaseTest {

    @Mock
    private AlertInstanceRepository alertInstanceRepository;

    private AcknowledgeAlertUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AcknowledgeAlertUseCase(alertInstanceRepository);
    }

    @Test
    @DisplayName("Should acknowledge alert when found")
    void shouldAcknowledgeAlert() {
        // Arrange
        var instance = new AlertInstance("default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.MEDIUM, "test");
        when(alertInstanceRepository.findById(instance.getId())).thenReturn(Optional.of(instance));
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute(instance.getId());

        // Assert
        assertThat(result.getStatus()).as("status after acknowledge").isEqualTo(AlertInstanceStatus.ACKNOWLEDGED);
    }

    @Test
    @DisplayName("Should throw when alert not found")
    void shouldThrowWhenNotFound() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(alertInstanceRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(id))
                .as("Non-existent alert should throw AlertInstanceNotFoundException")
                .isInstanceOf(AlertInstanceNotFoundException.class);
    }
}
