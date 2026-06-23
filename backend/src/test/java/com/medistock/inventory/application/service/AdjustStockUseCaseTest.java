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
@DisplayName("AdjustStockUseCase")
@SuppressWarnings("java:S5778")
class AdjustStockUseCaseTest {

    private static final String TENANT = "test-tenant";
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock private BatchRepository batchRepository;
    @Mock private MovementRepository movementRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Captor private ArgumentCaptor<Movement> movementCaptor;

    private AdjustStockUseCase useCase;
    private Batch activeBatch;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        useCase = new AdjustStockUseCase(batchRepository, movementRepository, eventPublisher);
        activeBatch = Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.valueOf(15.50));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should adjust stock upward with positive diff")
    void shouldAdjustStockWithPositiveDiff() {
        // Arrange
        activeBatch.allocate(30);
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute(activeBatch.getId(), 80, "Corrected after count");

        // Assert
        assertThat(result.getCurrentQuantity()).as("adjusted quantity").isEqualTo(80);
        verify(movementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getQuantity()).as("movement diff").isEqualTo(10);
        assertThat(movementCaptor.getValue().getType()).as("movement type").isEqualTo(MovementType.ADJUSTMENT);
        verify(eventPublisher).publishEvent(any(StockAdjustedEvent.class));
    }

    @Test
    @DisplayName("Should decrease stock with negative diff")
    void shouldDecreaseStock() {
        // Arrange
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute(activeBatch.getId(), 70, "Removed damaged units");

        // Assert
        assertThat(result.getCurrentQuantity()).as("decreased quantity").isEqualTo(70);
        verify(movementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getQuantity()).as("negative movement").isEqualTo(-30);
    }

    @Test
    @DisplayName("Should set to zero and deplete batch")
    void shouldSetToZeroAndDeplete() {
        // Arrange
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute(activeBatch.getId(), 0, "Fully depleted");

        // Assert
        assertThat(result.getCurrentQuantity()).as("depleted quantity").isZero();
        assertThat(result.getStatus()).as("depleted status").isEqualTo(BatchStatus.DEPLETED);
    }

    @Test
    @DisplayName("Should throw when batch not found")
    void shouldThrowWhenBatchNotFound() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(batchRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(id, 50, "test"))
                .as("Non-existent batch should throw")
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("Batch not found");

        verify(batchRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Should throw when batch is disposed")
    void shouldThrowWhenBatchDisposed() {
        // Arrange
        activeBatch.dispose();
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(activeBatch.getId(), 50, "test"))
                .as("Disposed batch should throw")
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("Cannot adjust");

        verify(batchRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when target quantity is negative")
    void shouldThrowWhenNegativeQuantity() {
        // Arrange
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(activeBatch.getId(), -5, "test"))
                .as("Negative target quantity should throw")
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("negative");
    }

    @Test
    @DisplayName("Should throw when target exceeds initial quantity")
    void shouldThrowWhenExceedsInitialQuantity() {
        // Arrange
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(activeBatch.getId(), 200, "test"))
                .as("Exceeding initial quantity should throw")
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("exceed initial");
    }
}
