package com.medistock.alerts.application;

import com.medistock.alerts.application.service.AcknowledgeAlertUseCase;
import com.medistock.alerts.domain.exception.AlertInstanceNotFoundException;
import com.medistock.alerts.domain.model.*;
import com.medistock.alerts.domain.port.AlertInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcknowledgeAlertUseCaseTest {

    @Mock
    private AlertInstanceRepository alertInstanceRepository;

    private AcknowledgeAlertUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AcknowledgeAlertUseCase(alertInstanceRepository);
    }

    @Test
    void shouldAcknowledgeAlert() {
        var instance = new AlertInstance("default", UUID.randomUUID(), UUID.randomUUID(), null,
                AlertSeverity.MEDIUM, "test");
        when(alertInstanceRepository.findById(instance.getId())).thenReturn(Optional.of(instance));
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute(instance.getId());
        assertThat(result.getStatus()).isEqualTo(AlertInstanceStatus.ACKNOWLEDGED);
    }

    @Test
    void shouldThrowWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(alertInstanceRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(id))
                .isInstanceOf(AlertInstanceNotFoundException.class);
    }
}
