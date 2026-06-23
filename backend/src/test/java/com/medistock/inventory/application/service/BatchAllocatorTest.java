package com.medistock.inventory.application.service;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.inventory.domain.model.*;
import com.medistock.inventory.domain.service.FefoQuerySpecification;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.inventory.domain.port.MovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchAllocator")
class BatchAllocatorTest {

    private static final String TENANT = "tenant-1";
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock private FefoQuerySpecification fefoQuery;
    @Mock private BatchRepository batchRepository;
    @Mock private MovementRepository movementRepository;
    @Captor private ArgumentCaptor<Movement> movementCaptor;

    private BatchAllocator allocator;
    private Batch batch1;
    private Batch batch2;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        allocator = new BatchAllocator(fefoQuery, batchRepository, movementRepository);

        batch1 = Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 50,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.valueOf(10));
        batch2 = Batch.receive(TENANT, PRODUCT_ID, "BATCH-002", 100,
                LocalDate.of(2028, Month.JUNE, 18), BigDecimal.valueOf(12));
    }

    @Test
    @DisplayName("Should allocate from single batch")
    void shouldAllocateFromSingleBatch() {
        // Arrange
        when(fefoQuery.findAvailableByProductOrderedByExpiry(PRODUCT_ID))
                .thenReturn(List.of(batch1));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        AllocationResult result = allocator.allocate(PRODUCT_ID, 30);

        // Assert
        assertTrue(result.fullyAllocated(), "fully allocated");
        assertEquals(30, result.totalAllocated(), "total allocated");
        assertEquals(1, result.lines().size(), "allocation lines count");
        verify(batchRepository, times(1)).save(any());
        verify(movementRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should allocate from multiple batches")
    void shouldAllocateFromMultipleBatches() {
        // Arrange
        when(fefoQuery.findAvailableByProductOrderedByExpiry(PRODUCT_ID))
                .thenReturn(List.of(batch1, batch2));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        AllocationResult result = allocator.allocate(PRODUCT_ID, 120);

        // Assert
        assertTrue(result.fullyAllocated(), "fully allocated");
        assertEquals(120, result.totalAllocated(), "total allocated");
        assertEquals(2, result.lines().size(), "allocation lines count");
        verify(batchRepository, times(2)).save(any());
        verify(movementRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Should record allocated movements")
    void shouldRecordAllocatedMovements() {
        // Arrange
        when(fefoQuery.findAvailableByProductOrderedByExpiry(PRODUCT_ID))
                .thenReturn(List.of(batch1));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        allocator.allocate(PRODUCT_ID, 30);

        // Assert
        verify(movementRepository, times(1)).save(movementCaptor.capture());
        Movement movement = movementCaptor.getValue();
        assertEquals(MovementType.ALLOCATED, movement.getType(), "movement type");
        assertEquals(-30, movement.getQuantity(), "movement quantity");
        assertEquals(batch1.getId(), movement.getBatchId(), "batch id");
    }

    @Test
    @DisplayName("Should throw when insufficient stock")
    void shouldThrowWhenInsufficientStock() {
        // Arrange
        when(fefoQuery.findAvailableByProductOrderedByExpiry(PRODUCT_ID))
                .thenReturn(List.of(batch1));

        // Act & Assert
        assertThrows(StockShortageException.class, () ->
                allocator.allocate(PRODUCT_ID, 100),
                "Insufficient stock should throw StockShortageException");
    }

    @Test
    @DisplayName("Should not save anything when stock shortage")
    void shouldNotSaveAnythingWhenShortage() {
        // Arrange
        when(fefoQuery.findAvailableByProductOrderedByExpiry(PRODUCT_ID))
                .thenReturn(List.of(batch1));

        // Act & Assert
        assertThrows(StockShortageException.class, () ->
                allocator.allocate(PRODUCT_ID, 200));

        verify(batchRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should propagate OptimisticLockingFailureException")
    void shouldPropagateExceptionOnOptimisticLockFailure() {
        // Arrange
        when(fefoQuery.findAvailableByProductOrderedByExpiry(PRODUCT_ID))
                .thenReturn(List.of(batch1));
        when(batchRepository.save(any()))
                .thenThrow(new OptimisticLockingFailureException("lock conflict"));

        // Act & Assert
        assertThrows(OptimisticLockingFailureException.class, () ->
                allocator.allocate(PRODUCT_ID, 30),
                "Optimistic lock failure should propagate");

        verify(batchRepository, times(1)).save(any());
    }
}
