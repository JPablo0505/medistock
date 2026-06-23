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
    @DisplayName("Should release batch from quarantine")
    void shouldReleaseFromQuarantine() {
        // Arrange
        when(batchRepository.findById(quarantinedBatch.getId())).thenReturn(Optional.of(quarantinedBatch));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute(quarantinedBatch.getId(), "Cleared inspection");

        // Assert
        assertThat(result.getStatus()).as("status after release").isEqualTo(BatchStatus.ACTIVE);
        assertThat(result.getCurrentQuantity()).as("current quantity").isEqualTo(100);
        verify(movementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getType()).as("movement type").isEqualTo(MovementType.QUARANTINE_OUT);
    }

    @Test
    @DisplayName("Should throw when batch not found")
    void shouldThrowWhenBatchNotFound() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(batchRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(id, "reason"))
                .as("Non-existent batch should throw")
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("Batch not found");
    }

    @Test
    @DisplayName("Should throw when batch is not quarantined")
    void shouldThrowWhenNotQuarantined() {
        // Arrange
        var activeBatch = Batch.receive(TENANT, UUID.randomUUID(), "BATCH-ACTIVE", 50,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.TEN);
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(activeBatch.getId(), "release active"))
                .as("Releasing non-quarantined batch should throw")
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("not QUARANTINED");

        verify(batchRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when batch is disposed")
    void shouldThrowWhenDisposed() {
        // Arrange
        var disposedBatch = Batch.receive(TENANT, UUID.randomUUID(), "BATCH-DISP", 50,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.TEN);
        disposedBatch.dispose();
        when(batchRepository.findById(disposedBatch.getId())).thenReturn(Optional.of(disposedBatch));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(disposedBatch.getId(), "release disposed"))
                .as("Releasing disposed batch should throw")
                .isInstanceOf(InventoryDomainException.class);
    }
}
