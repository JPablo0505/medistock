package com.medistock.alerts.application;

import com.medistock.alerts.application.service.UpdateAlertRuleUseCase;
import com.medistock.alerts.domain.exception.AlertRuleNotFoundException;
import com.medistock.alerts.domain.exception.InvalidAlertRuleException;
import com.medistock.alerts.domain.model.AlertRule;
import com.medistock.alerts.domain.model.AlertRuleType;
import com.medistock.alerts.domain.port.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
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
    void shouldUpdateName() {
        when(alertRuleRepository.findById(existingRule.getId())).thenReturn(Optional.of(existingRule));
        when(alertRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute(existingRule.getId(), "New Name", null, null, null);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getDescription()).isEqualTo("Original Desc");
    }

    @Test
    void shouldUpdateDescription() {
        when(alertRuleRepository.findById(existingRule.getId())).thenReturn(Optional.of(existingRule));
        when(alertRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute(existingRule.getId(), null, "New Desc", null, null);

        assertThat(result.getDescription()).isEqualTo("New Desc");
    }

    @Test
    void shouldUpdateType() {
        when(alertRuleRepository.findById(existingRule.getId())).thenReturn(Optional.of(existingRule));
        when(alertRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute(existingRule.getId(), null, null, "LOW_STOCK", null);

        assertThat(result.getType()).isEqualTo(AlertRuleType.LOW_STOCK);
    }

    @Test
    void shouldUpdateConfig() {
        when(alertRuleRepository.findById(existingRule.getId())).thenReturn(Optional.of(existingRule));
        when(alertRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var newConfig = Map.<String, Object>of("expirationDays", 15);
        var result = useCase.execute(existingRule.getId(), null, null, null, newConfig);

        assertThat(result.getExpirationDays()).isEqualTo(15);
    }

    @Test
    void shouldThrowOnInvalidType() {
        when(alertRuleRepository.findById(existingRule.getId())).thenReturn(Optional.of(existingRule));

        assertThatThrownBy(() ->
                useCase.execute(existingRule.getId(), null, null, "INVALID_TYPE", null))
                .isInstanceOf(InvalidAlertRuleException.class);

        verify(alertRuleRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenRuleNotFound() {
        UUID id = UUID.randomUUID();
        when(alertRuleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                useCase.execute(id, "Name", null, null, null))
                .isInstanceOf(AlertRuleNotFoundException.class);

        verify(alertRuleRepository, never()).save(any());
    }

    @Test
    void shouldToggleActiveToInactive() {
        when(alertRuleRepository.findById(existingRule.getId())).thenReturn(Optional.of(existingRule));
        when(alertRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.toggleActive(existingRule.getId());

        assertThat(existingRule.isActive()).isFalse();
    }

    @Test
    void shouldToggleInactiveToActive() {
        existingRule.deactivate();
        when(alertRuleRepository.findById(existingRule.getId())).thenReturn(Optional.of(existingRule));
        when(alertRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.toggleActive(existingRule.getId());

        assertThat(existingRule.isActive()).isTrue();
    }

    @Test
    void shouldThrowOnToggleWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(alertRuleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.toggleActive(id))
                .isInstanceOf(AlertRuleNotFoundException.class);

        verify(alertRuleRepository, never()).save(any());
    }
}
