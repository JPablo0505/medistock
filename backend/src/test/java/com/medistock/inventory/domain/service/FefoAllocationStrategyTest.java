package com.medistock.inventory.domain.service;

import com.medistock.inventory.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FefoAllocationStrategy")
class FefoAllocationStrategyTest {

    private static final String TENANT = "tenant-1";
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final LocalDate EXPIRY_2027 = LocalDate.of(2027, Month.JUNE, 18);
    private static final LocalDate EXPIRY_2028 = LocalDate.of(2028, Month.JUNE, 18);
    private static final LocalDate EXPIRY_2026 = LocalDate.of(2026, Month.DECEMBER, 1);

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
    @DisplayName("Should allocate from a single batch")
    void shouldAllocateFromSingleBatch() {
        // Arrange
        List<Batch> batches = sortFefo(List.of(
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100, EXPIRY_2027, BigDecimal.valueOf(10))
        ));

        // Act
        AllocationResult result = strategy.allocate(batches, PRODUCT_ID, 30);

        // Assert
        assertTrue(result.fullyAllocated(), "fully allocated");
        assertEquals(30, result.totalAllocated(), "total allocated");
        assertEquals(1, result.lines().size(), "allocation lines");
        assertEquals("BATCH-001", result.lines().getFirst().batchCode(), "batch code");
    }

    @Test
    @DisplayName("Should allocate from multiple batches in FEFO order")
    void shouldAllocateFromMultipleBatchesInFefoOrder() {
        // Arrange
        Batch batch1 = Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 50, EXPIRY_2028, BigDecimal.valueOf(10));
        Batch batch2 = Batch.receive(TENANT, PRODUCT_ID, "BATCH-002", 100, EXPIRY_2027, BigDecimal.valueOf(12));

        List<Batch> batches = sortFefo(List.of(batch1, batch2));

        // Act
        AllocationResult result = strategy.allocate(batches, PRODUCT_ID, 120);

        // Assert
        assertTrue(result.fullyAllocated(), "fully allocated");
        assertEquals(120, result.totalAllocated(), "total allocated");
        assertEquals(2, result.lines().size(), "allocation lines");
        assertEquals("BATCH-002", result.lines().get(0).batchCode(), "first batch (earliest expiry)");
        assertEquals(100, result.lines().get(0).quantityAllocated(), "first batch quantity");
        assertEquals("BATCH-001", result.lines().get(1).batchCode(), "second batch (later expiry)");
        assertEquals(20, result.lines().get(1).quantityAllocated(), "second batch quantity");
    }

    @Test
    @DisplayName("Should respect FEFO order with three batches")
    void shouldRespectFefoOrderWithThreeBatches() {
        // Arrange
        Batch batch1 = Batch.receive(TENANT, PRODUCT_ID, "OLD", 30, EXPIRY_2026, BigDecimal.valueOf(8));
        Batch batch2 = Batch.receive(TENANT, PRODUCT_ID, "MID", 50, EXPIRY_2027, BigDecimal.valueOf(10));
        Batch batch3 = Batch.receive(TENANT, PRODUCT_ID, "NEW", 100, EXPIRY_2028, BigDecimal.valueOf(12));

        List<Batch> batches = sortFefo(List.of(batch1, batch2, batch3));

        // Act
        AllocationResult result = strategy.allocate(batches, PRODUCT_ID, 80);

        // Assert
        assertTrue(result.fullyAllocated(), "fully allocated");
        assertEquals(80, result.totalAllocated(), "total allocated");
        assertEquals(2, result.lines().size(), "allocation lines");
        assertEquals("OLD", result.lines().get(0).batchCode(), "first batch (oldest)");
        assertEquals("MID", result.lines().get(1).batchCode(), "second batch");
        assertEquals(30, result.lines().get(0).quantityAllocated(), "old batch fully consumed");
        assertEquals(50, result.lines().get(1).quantityAllocated(), "mid batch partially consumed");
        assertEquals(100, batch3.getCurrentQuantity(), "newest batch untouched");
    }

    @Test
    @DisplayName("Should throw when insufficient stock")
    void shouldThrowWhenInsufficientStock() {
        // Arrange
        List<Batch> batches = sortFefo(List.of(
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 10, EXPIRY_2027, BigDecimal.valueOf(10))
        ));

        // Act & Assert
        assertThrows(StockShortageException.class, () ->
                strategy.allocate(batches, PRODUCT_ID, 100),
                "Insufficient stock should throw StockShortageException");
    }

    @Test
    @DisplayName("Should throw when no stock available")
    void shouldThrowWhenNoStock() {
        // Act & Assert
        assertThrows(StockShortageException.class, () ->
                strategy.allocate(List.of(), PRODUCT_ID, 1),
                "No stock should throw StockShortageException");
    }

    @Test
    @DisplayName("Should allocate exact quantity")
    void shouldAllocateExactQuantity() {
        // Arrange
        List<Batch> batches = sortFefo(List.of(
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 50, EXPIRY_2027, BigDecimal.valueOf(10))
        ));

        // Act
        AllocationResult result = strategy.allocate(batches, PRODUCT_ID, 50);

        // Assert
        assertTrue(result.fullyAllocated(), "fully allocated");
        assertEquals(50, result.totalAllocated(), "total allocated");
    }

    @Test
    @DisplayName("Should mutate batch quantities on allocation")
    void shouldMutateBatchQuantitiesOnAllocation() {
        // Arrange
        Batch batch = Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100, EXPIRY_2027, BigDecimal.valueOf(10));
        List<Batch> batches = sortFefo(List.of(batch));

        // Act
        strategy.allocate(batches, PRODUCT_ID, 30);

        // Assert
        assertEquals(70, batch.getCurrentQuantity(), "remaining quantity");
        assertEquals(BatchStatus.ACTIVE, batch.getStatus(), "status should remain ACTIVE");
    }

    @Test
    @DisplayName("Should deplete batch when fully consumed")
    void shouldDepleteBatchWhenFullyConsumed() {
        // Arrange
        Batch batch = Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 50, EXPIRY_2027, BigDecimal.valueOf(10));
        List<Batch> batches = sortFefo(List.of(batch));

        // Act
        strategy.allocate(batches, PRODUCT_ID, 50);

        // Assert
        assertEquals(0, batch.getCurrentQuantity(), "quantity after full consumption");
        assertEquals(BatchStatus.DEPLETED, batch.getStatus(), "status should be DEPLETED");
    }

    @Test
    @DisplayName("Should compute total cost")
    void shouldComputeTotalCost() {
        // Arrange
        List<Batch> batches = sortFefo(List.of(
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100, EXPIRY_2027, BigDecimal.valueOf(15.50))
        ));

        // Act
        AllocationResult result = strategy.allocate(batches, PRODUCT_ID, 10);

        // Assert
        assertEquals(0, BigDecimal.valueOf(155.0).compareTo(result.totalCost()),
                "total cost should be 10 * 15.50");
    }
}
