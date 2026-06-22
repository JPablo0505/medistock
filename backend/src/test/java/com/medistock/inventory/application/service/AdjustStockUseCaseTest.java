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
    void shouldAdjustStockWithPositiveDiff() {
        activeBatch.allocate(30); // now at 70
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute(activeBatch.getId(), 80, "Corrected after count");

        assertThat(result.getCurrentQuantity()).isEqualTo(80);
        verify(movementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getQuantity()).isEqualTo(10);
        assertThat(movementCaptor.getValue().getType()).isEqualTo(MovementType.ADJUSTMENT);
        verify(eventPublisher).publishEvent(any(StockAdjustedEvent.class));
    }

    @Test
    void shouldDecreaseStock() {
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute(activeBatch.getId(), 70, "Removed damaged units");

        assertThat(result.getCurrentQuantity()).isEqualTo(70);
        verify(movementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getQuantity()).isEqualTo(-30);
    }

    @Test
    void shouldSetToZeroAndDeplete() {
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute(activeBatch.getId(), 0, "Fully depleted");

        assertThat(result.getCurrentQuantity()).isEqualTo(0);
        assertThat(result.getStatus()).isEqualTo(BatchStatus.DEPLETED);
    }

    @Test
    void shouldThrowWhenBatchNotFound() {
        UUID id = UUID.randomUUID();
        when(batchRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, 50, "test"))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("Batch not found");

        verify(batchRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldThrowWhenBatchDisposed() {
        activeBatch.dispose();
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        assertThatThrownBy(() -> useCase.execute(activeBatch.getId(), 50, "test"))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("Cannot adjust");

        verify(batchRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenNegativeQuantity() {
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        assertThatThrownBy(() -> useCase.execute(activeBatch.getId(), -5, "test"))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void shouldThrowWhenExceedsInitialQuantity() {
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        assertThatThrownBy(() -> useCase.execute(activeBatch.getId(), 200, "test"))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("exceed initial");
    }
}
