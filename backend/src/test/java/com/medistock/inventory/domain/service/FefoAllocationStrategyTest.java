package com.medistock.inventory.domain.service;

import com.medistock.inventory.domain.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FefoAllocationStrategyTest {

    private static final String TENANT = "tenant-1";
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final LocalDate EXPIRY_2027 = LocalDate.of(2027, 6, 18);
    private static final LocalDate EXPIRY_2028 = LocalDate.of(2028, 6, 18);
    private static final LocalDate EXPIRY_2026 = LocalDate.of(2026, 12, 1);

    private final FefoAllocationStrategy strategy = new FefoAllocationStrategy();

    private List<Batch> sortFefo(List<Batch> batches) {
        return batches.stream()
                .filter(b -> b.getStatus() == BatchStatus.ACTIVE && b.getCurrentQuantity() > 0)
                .sorted((a, b) -> {
                    int cmp = a.getExpirationDate().compareTo(b.getExpirationDate());
                    if (cmp == 0) {
                        cmp = Integer.compare(a.getCurrentQuantity(), b.getCurrentQuantity());
                    }
                    return cmp;
                })
                .toList();
    }

    @Test
    void shouldAllocateFromSingleBatch() {
        List<Batch> batches = sortFefo(List.of(
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100, EXPIRY_2027, BigDecimal.valueOf(10))
        ));

        AllocationResult result = strategy.allocate(batches, PRODUCT_ID, 30);

        assertTrue(result.fullyAllocated());
        assertEquals(30, result.totalAllocated());
        assertEquals(1, result.lines().size());
        assertEquals("BATCH-001", result.lines().getFirst().batchCode());
    }

    @Test
    void shouldAllocateFromMultipleBatchesInFefoOrder() {
        Batch batch1 = Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 50, EXPIRY_2028, BigDecimal.valueOf(10));
        Batch batch2 = Batch.receive(TENANT, PRODUCT_ID, "BATCH-002", 100, EXPIRY_2027, BigDecimal.valueOf(12));

        List<Batch> batches = sortFefo(List.of(batch1, batch2));

        AllocationResult result = strategy.allocate(batches, PRODUCT_ID, 120);

        assertTrue(result.fullyAllocated());
        assertEquals(120, result.totalAllocated());
        assertEquals(2, result.lines().size());
        assertEquals("BATCH-002", result.lines().get(0).batchCode());
        assertEquals(100, result.lines().get(0).quantityAllocated());
        assertEquals("BATCH-001", result.lines().get(1).batchCode());
        assertEquals(20, result.lines().get(1).quantityAllocated());
    }

    @Test
    void shouldRespectFefoOrderWithThreeBatches() {
        Batch batch1 = Batch.receive(TENANT, PRODUCT_ID, "OLD", 30, EXPIRY_2026, BigDecimal.valueOf(8));
        Batch batch2 = Batch.receive(TENANT, PRODUCT_ID, "MID", 50, EXPIRY_2027, BigDecimal.valueOf(10));
        Batch batch3 = Batch.receive(TENANT, PRODUCT_ID, "NEW", 100, EXPIRY_2028, BigDecimal.valueOf(12));

        List<Batch> batches = sortFefo(List.of(batch1, batch2, batch3));

        AllocationResult result = strategy.allocate(batches, PRODUCT_ID, 80);

        assertTrue(result.fullyAllocated());
        assertEquals(80, result.totalAllocated());
        assertEquals(2, result.lines().size());
        assertEquals("OLD", result.lines().get(0).batchCode());
        assertEquals("MID", result.lines().get(1).batchCode());
        assertEquals(30, result.lines().get(0).quantityAllocated());
        assertEquals(50, result.lines().get(1).quantityAllocated());
        assertEquals(100, batch3.getCurrentQuantity());
    }

    @Test
    void shouldThrowWhenInsufficientStock() {
        List<Batch> batches = sortFefo(List.of(
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 10, EXPIRY_2027, BigDecimal.valueOf(10))
        ));

        assertThrows(StockShortageException.class, () ->
                strategy.allocate(batches, PRODUCT_ID, 100));
    }

    @Test
    void shouldThrowWhenNoStock() {
        assertThrows(StockShortageException.class, () ->
                strategy.allocate(List.of(), PRODUCT_ID, 1));
    }

    @Test
    void shouldAllocateExactQuantity() {
        List<Batch> batches = sortFefo(List.of(
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 50, EXPIRY_2027, BigDecimal.valueOf(10))
        ));

        AllocationResult result = strategy.allocate(batches, PRODUCT_ID, 50);

        assertTrue(result.fullyAllocated());
        assertEquals(50, result.totalAllocated());
    }

    @Test
    void shouldMutateBatchQuantitiesOnAllocation() {
        Batch batch = Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100, EXPIRY_2027, BigDecimal.valueOf(10));
        List<Batch> batches = sortFefo(List.of(batch));

        strategy.allocate(batches, PRODUCT_ID, 30);

        assertEquals(70, batch.getCurrentQuantity());
        assertEquals(BatchStatus.ACTIVE, batch.getStatus());
    }

    @Test
    void shouldDepleteBatchWhenFullyConsumed() {
        Batch batch = Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 50, EXPIRY_2027, BigDecimal.valueOf(10));
        List<Batch> batches = sortFefo(List.of(batch));

        strategy.allocate(batches, PRODUCT_ID, 50);

        assertEquals(0, batch.getCurrentQuantity());
        assertEquals(BatchStatus.DEPLETED, batch.getStatus());
    }

    @Test
    void shouldComputeTotalCost() {
        List<Batch> batches = sortFefo(List.of(
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100, EXPIRY_2027, BigDecimal.valueOf(15.50))
        ));

        AllocationResult result = strategy.allocate(batches, PRODUCT_ID, 10);

        assertEquals(0, BigDecimal.valueOf(155.0).compareTo(result.totalCost()));
    }
}
