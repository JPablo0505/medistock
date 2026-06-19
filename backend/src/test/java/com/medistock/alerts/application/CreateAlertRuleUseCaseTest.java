package com.medistock.alerts.application;

import com.medistock.alerts.application.service.CreateAlertRuleUseCase;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateAlertRuleUseCaseTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    private CreateAlertRuleUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateAlertRuleUseCase(alertRuleRepository);
    }

    @Test
    void shouldCreateRule() {
        var config = Map.<String, Object>of("expirationDays", 30);
        when(alertRuleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute("tenant-1", "Exp Alert", "Desc", "EXPIRATION", config);

        assertThat(result.getName()).isEqualTo("Exp Alert");
        assertThat(result.getType()).isEqualTo(AlertRuleType.EXPIRATION);
        verify(alertRuleRepository).save(any());
    }

    @Test
    void shouldThrowOnInvalidType() {
        assertThatThrownBy(() ->
                useCase.execute("t", "Test", "Desc", "INVALID", Map.<String, Object>of("k", "v")))
                .isInstanceOf(InvalidAlertRuleException.class);
        verify(alertRuleRepository, never()).save(any());
    }
}
