package com.medistock.inventory.application.service;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.inventory.domain.model.*;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.inventory.domain.port.MovementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuarantineUseCaseTest {

    private static final String TENANT = "test-tenant";

    @Mock private BatchRepository batchRepository;
    @Mock private MovementRepository movementRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Captor private ArgumentCaptor<Movement> movementCaptor;

    private QuarantineUseCase useCase;
    private Batch activeBatch;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        useCase = new QuarantineUseCase(batchRepository, movementRepository, eventPublisher);
        activeBatch = Batch.receive(TENANT, UUID.randomUUID(), "BATCH-001", 100,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.valueOf(15.50));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldQuarantineBatch() {
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute(activeBatch.getId(), "Quality inspection failed");

        assertThat(result.getStatus()).isEqualTo(BatchStatus.QUARANTINED);
        assertThat(result.getCurrentQuantity()).isEqualTo(100);
        verify(movementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getType()).isEqualTo(MovementType.QUARANTINE_IN);
        verify(eventPublisher).publishEvent(any(BatchQuarantinedEvent.class));
    }

    @Test
    void shouldThrowWhenBatchNotFound() {
        UUID id = UUID.randomUUID();
        when(batchRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, "reason"))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("Batch not found");
    }

    @Test
    void shouldThrowWhenAlreadyQuarantined() {
        activeBatch.quarantine();
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        assertThatThrownBy(() -> useCase.execute(activeBatch.getId(), "double quarantine"))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("already QUARANTINED");

        verify(batchRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenDisposed() {
        activeBatch.dispose();
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        assertThatThrownBy(() -> useCase.execute(activeBatch.getId(), "can't quarantine disposed"))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("Cannot quarantine a DISPOSED batch");
    }
}
