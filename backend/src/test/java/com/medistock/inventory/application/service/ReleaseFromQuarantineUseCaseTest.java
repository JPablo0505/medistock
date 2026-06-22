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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReleaseFromQuarantineUseCaseTest {

    private static final String TENANT = "test-tenant";

    @Mock private BatchRepository batchRepository;
    @Mock private MovementRepository movementRepository;
    @Captor private ArgumentCaptor<Movement> movementCaptor;

    private ReleaseFromQuarantineUseCase useCase;
    private Batch quarantinedBatch;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        useCase = new ReleaseFromQuarantineUseCase(batchRepository, movementRepository);
        quarantinedBatch = Batch.receive(TENANT, UUID.randomUUID(), "BATCH-Q-001", 100,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.valueOf(15.50));
        quarantinedBatch.quarantine();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldReleaseFromQuarantine() {
        when(batchRepository.findById(quarantinedBatch.getId())).thenReturn(Optional.of(quarantinedBatch));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute(quarantinedBatch.getId(), "Cleared inspection");

        assertThat(result.getStatus()).isEqualTo(BatchStatus.ACTIVE);
        assertThat(result.getCurrentQuantity()).isEqualTo(100);
        verify(movementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getType()).isEqualTo(MovementType.QUARANTINE_OUT);
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
    void shouldThrowWhenNotQuarantined() {
        var activeBatch = Batch.receive(TENANT, UUID.randomUUID(), "BATCH-ACTIVE", 50,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.TEN);
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        assertThatThrownBy(() -> useCase.execute(activeBatch.getId(), "release active"))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("not QUARANTINED");

        verify(batchRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenDisposed() {
        var disposedBatch = Batch.receive(TENANT, UUID.randomUUID(), "BATCH-DISP", 50,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.TEN);
        disposedBatch.dispose();
        when(batchRepository.findById(disposedBatch.getId())).thenReturn(Optional.of(disposedBatch));

        assertThatThrownBy(() -> useCase.execute(disposedBatch.getId(), "release disposed"))
                .isInstanceOf(InventoryDomainException.class);
    }
}
