package com.medistock.inventory.application.service;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.inventory.domain.model.*;
import com.medistock.inventory.domain.service.FefoQuerySpecification;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.inventory.domain.port.MovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchAllocatorTest {

    private static final String TENANT = "tenant-1";
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock private FefoQuerySpecification fefoQuery;
    @Mock private BatchRepository batchRepository;
    @Mock private MovementRepository movementRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Captor private ArgumentCaptor<Movement> movementCaptor;

    private BatchAllocator allocator;
    private Batch batch1;
    private Batch batch2;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        allocator = new BatchAllocator(fefoQuery, batchRepository, movementRepository, eventPublisher);

        batch1 = Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 50,
                LocalDate.of(2027, 6, 18), BigDecimal.valueOf(10));
        batch2 = Batch.receive(TENANT, PRODUCT_ID, "BATCH-002", 100,
                LocalDate.of(2028, 6, 18), BigDecimal.valueOf(12));
    }

    @Test
    void shouldAllocateFromSingleBatch() {
        when(fefoQuery.findAvailableByProductOrderedByExpiry(PRODUCT_ID))
                .thenReturn(List.of(batch1));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AllocationResult result = allocator.allocate(PRODUCT_ID, 30);

        assertTrue(result.fullyAllocated());
        assertEquals(30, result.totalAllocated());
        assertEquals(1, result.lines().size());
        verify(batchRepository, times(1)).save(any());
        verify(movementRepository, times(1)).save(any());
    }

    @Test
    void shouldAllocateFromMultipleBatches() {
        when(fefoQuery.findAvailableByProductOrderedByExpiry(PRODUCT_ID))
                .thenReturn(List.of(batch1, batch2));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AllocationResult result = allocator.allocate(PRODUCT_ID, 120);

        assertTrue(result.fullyAllocated());
        assertEquals(120, result.totalAllocated());
        assertEquals(2, result.lines().size());
        verify(batchRepository, times(2)).save(any());
        verify(movementRepository, times(2)).save(any());
    }

    @Test
    void shouldRecordAllocatedMovements() {
        when(fefoQuery.findAvailableByProductOrderedByExpiry(PRODUCT_ID))
                .thenReturn(List.of(batch1));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        allocator.allocate(PRODUCT_ID, 30);

        verify(movementRepository, times(1)).save(movementCaptor.capture());
        Movement movement = movementCaptor.getValue();
        assertEquals(MovementType.ALLOCATED, movement.getType());
        assertEquals(-30, movement.getQuantity());
        assertEquals(batch1.getId(), movement.getBatchId());
    }

    @Test
    void shouldThrowWhenInsufficientStock() {
        when(fefoQuery.findAvailableByProductOrderedByExpiry(PRODUCT_ID))
                .thenReturn(List.of(batch1));

        assertThrows(StockShortageException.class, () ->
                allocator.allocate(PRODUCT_ID, 100));
    }

    @Test
    void shouldNotSaveAnythingWhenShortage() {
        when(fefoQuery.findAvailableByProductOrderedByExpiry(PRODUCT_ID))
                .thenReturn(List.of(batch1));

        assertThrows(StockShortageException.class, () ->
                allocator.allocate(PRODUCT_ID, 200));

        verify(batchRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void shouldPropagateExceptionOnOptimisticLockFailure() {
        // @Retryable requires Spring AOP proxy, but with MockitoExtension
        // we test the raw behavior: OptimisticLockingFailureException propagates
        when(fefoQuery.findAvailableByProductOrderedByExpiry(PRODUCT_ID))
                .thenReturn(List.of(batch1));
        when(batchRepository.save(any()))
                .thenThrow(new OptimisticLockingFailureException("lock conflict"));

        assertThrows(OptimisticLockingFailureException.class, () ->
                allocator.allocate(PRODUCT_ID, 30));

        // Without AOP proxy, save is called only once before the exception propagates
        verify(batchRepository, times(1)).save(any());
    }
}
