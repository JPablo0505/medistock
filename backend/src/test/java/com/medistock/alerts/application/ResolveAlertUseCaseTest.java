package com.medistock.alerts.application;

import com.medistock.alerts.application.service.ResolveAlertUseCase;
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
@DisplayName("ResolveAlertUseCase")
class ResolveAlertUseCaseTest {

    @Mock
    private AlertInstanceRepository alertInstanceRepository;

    private ResolveAlertUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ResolveAlertUseCase(alertInstanceRepository);
    }

    @Test
    @DisplayName("Should resolve alert when found")
    void shouldResolveAlert() {
        // Arrange
        var instance = new AlertInstance("default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.MEDIUM, "test");
        when(alertInstanceRepository.findById(instance.getId())).thenReturn(Optional.of(instance));
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute(instance.getId());

        // Assert
        assertThat(result.getStatus()).as("status after resolve").isEqualTo(AlertInstanceStatus.RESOLVED);
        assertThat(result.getResolvedAt()).as("resolved timestamp").isNotNull();
        verify(alertInstanceRepository).save(instance);
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

        verify(alertInstanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should be idempotent when alert already resolved")
    void shouldBeIdempotentWhenAlreadyResolved() {
        // Arrange
        var instance = new AlertInstance("default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.LOW, "msg");
        instance.resolve();
        when(alertInstanceRepository.findById(instance.getId())).thenReturn(Optional.of(instance));
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute(instance.getId());

        // Assert
        assertThat(result.getStatus()).as("status after re-resolve").isEqualTo(AlertInstanceStatus.RESOLVED);
    }
}
