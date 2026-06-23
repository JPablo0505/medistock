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
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WriteOffUseCase")
class WriteOffUseCaseTest {

    private static final String TENANT = "test-tenant";

    @Mock private BatchRepository batchRepository;
    @Mock private MovementRepository movementRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Captor private ArgumentCaptor<Movement> movementCaptor;

    private WriteOffUseCase useCase;
    private Batch activeBatch;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        useCase = new WriteOffUseCase(batchRepository, movementRepository, eventPublisher);
        activeBatch = Batch.receive(TENANT, UUID.randomUUID(), "BATCH-001", 100,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.valueOf(15.50));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should dispose active batch")
    void shouldDisposeBatch() {
        // Arrange
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute(activeBatch.getId(), "Contamination detected");

        // Assert
        assertThat(result.getStatus()).as("status after dispose").isEqualTo(BatchStatus.DISPOSED);
        assertThat(result.getCurrentQuantity()).as("current quantity after dispose").isEqualTo(0);
        verify(movementRepository).save(movementCaptor.capture());
        Movement movement = movementCaptor.getValue();
        assertThat(movement.getType()).as("movement type").isEqualTo(MovementType.WRITE_OFF);
        assertThat(movement.getQuantity()).as("movement quantity").isEqualTo(-100);
        verify(eventPublisher).publishEvent(any(BatchDisposedEvent.class));
    }

    @Test
    @DisplayName("Should throw when batch is already disposed")
    void shouldThrowWhenAlreadyDisposed() {
        // Arrange
        activeBatch.dispose();
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(activeBatch.getId(), "double dispose"))
                .as("Double dispose should throw")
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("already DISPOSED");

        verify(batchRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
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
    @DisplayName("Should throw when batch is already expired")
    void shouldThrowWhenAlreadyExpired() {
        // Arrange
        activeBatch.markExpired();
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(activeBatch.getId(), "write off expired"))
                .as("Disposing expired batch should throw")
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("already EXPIRED");
    }
}
