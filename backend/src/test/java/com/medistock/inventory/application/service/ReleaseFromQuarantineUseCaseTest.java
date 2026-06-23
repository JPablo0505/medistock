package com.medistock.inventory.application.service;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.inventory.domain.model.*;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.inventory.domain.port.MovementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
@DisplayName("ReleaseFromQuarantineUseCase")
@SuppressWarnings("java:S5778")
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
        var batch = Batch.receive(TENANT, UUID.randomUUID(), "BATCH-Q", 50,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.valueOf(20.00));
        batch.quarantine();
        quarantinedBatch = batch;
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should release quarantined batch back to active")
    void shouldReleaseFromQuarantine() {
        // Arrange
        when(batchRepository.findById(quarantinedBatch.getId())).thenReturn(Optional.of(quarantinedBatch));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute(quarantinedBatch.getId(), "Quality passed");

        // Assert
        assertThat(result.getStatus()).as("status after release").isEqualTo(BatchStatus.ACTIVE);
        assertThat(result.getCurrentQuantity()).as("quantity preserved").isEqualTo(50);
        verify(movementRepository).save(movementCaptor.capture());
        Movement movement = movementCaptor.getValue();
        assertThat(movement.getType()).as("movement type").isEqualTo(MovementType.QUARANTINE_OUT);
        assertThat(movement.getQuantity()).as("movement quantity").isZero();
    }

    @Test
    @DisplayName("Should throw when batch is not in quarantine")
    void shouldThrowWhenNotQuarantined() {
        // Arrange
        var activeBatch = Batch.receive(TENANT, UUID.randomUUID(), "BATCH-A", 10,
                LocalDate.of(2027, Month.JUNE, 1), BigDecimal.TEN);
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(activeBatch.getId(), "release"))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("QUARANTINED");

        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when batch not found")
    void shouldThrowWhenBatchNotFound() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(batchRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(id, "reason"))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("Batch not found");
    }
}
