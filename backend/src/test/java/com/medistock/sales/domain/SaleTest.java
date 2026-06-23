package com.medistock.sales.domain;

import com.medistock.sales.domain.exception.SaleAlreadyVoidedException;
import com.medistock.sales.domain.exception.SaleDomainException;
import com.medistock.sales.domain.model.BatchAllocation;
import com.medistock.sales.domain.model.Sale;
import com.medistock.sales.domain.model.SaleItem;
import com.medistock.sales.domain.model.SaleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Sale domain entity")
@SuppressWarnings("java:S5778")
class SaleTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();

    private BatchAllocation sampleAllocation() {
        return new BatchAllocation(BATCH_ID, "LOT-001", LocalDate.of(2027, Month.JUNE, 1), 10, BigDecimal.valueOf(5.00));
    }

    private SaleItem sampleItem() {
        return new SaleItem(PRODUCT_ID, "Ibuprofeno 400mg", "IBU-400", 10, BigDecimal.valueOf(15.50),
                List.of(sampleAllocation()));
    }

    @Test
    @DisplayName("Should create sale with valid items")
    void shouldCreateSaleWithValidItems() {
        // Arrange & Act
        Sale sale = Sale.create("tenant-1", "user-1", List.of(sampleItem()));

        // Assert
        assertNotNull(sale.getSaleId(), "sale id");
        assertEquals("tenant-1", sale.getTenantId(), "tenant id");
        assertEquals(SaleStatus.COMPLETED, sale.getStatus(), "status");
        assertEquals(1, sale.getItemCount(), "item count");
        assertEquals(BigDecimal.valueOf(155.00).stripTrailingZeros(), sale.getTotal().stripTrailingZeros(), "total");
        assertEquals(BigDecimal.valueOf(155.00).stripTrailingZeros(), sale.getSubtotal().stripTrailingZeros(), "subtotal");
    }

    @Test
    @DisplayName("Should throw when creating sale with empty items")
    void shouldThrowWhenCreatingWithEmptyItems() {
        // Act & Assert
        assertThrows(SaleDomainException.class,
                () -> Sale.create("tenant-1", "user-1", List.of()),
                "Empty items should throw SaleDomainException");
    }

    @Test
    @DisplayName("Should throw when item quantity is zero")
    void shouldThrowWhenItemQuantityIsZero() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> new SaleItem(PRODUCT_ID, "Test", "TST", 0, BigDecimal.TEN, List.of(sampleAllocation())),
                "Zero quantity item should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Total should equal sum of item totals")
    void totalShouldEqualSumOfItemTotals() {
        // Arrange
        BatchAllocation alloc1 = new BatchAllocation(UUID.randomUUID(), "LOT-001", LocalDate.of(2027, Month.JUNE, 1), 5, BigDecimal.valueOf(5.00));
        BatchAllocation alloc2 = new BatchAllocation(UUID.randomUUID(), "LOT-002", LocalDate.of(2027, Month.JULY, 1), 3, BigDecimal.valueOf(5.00));

        SaleItem item1 = new SaleItem(PRODUCT_ID, "Ibuprofeno 400mg", "IBU-400", 5, BigDecimal.valueOf(15.50),
                List.of(alloc1));
        SaleItem item2 = new SaleItem(UUID.randomUUID(), "Paracetamol 500mg", "PARA-500", 3, BigDecimal.valueOf(8.90),
                List.of(alloc2));

        // Act
        Sale sale = Sale.create("tenant-1", "user-1", List.of(item1, item2));

        // Assert
        BigDecimal expected = BigDecimal.valueOf(5 * 15.50 + 3 * 8.90);
        assertEquals(0, sale.getTotal().compareTo(expected), "total should match expected sum");
    }

    @Test
    @DisplayName("Should throw when allocation sum mismatches item quantity")
    void shouldThrowWhenAllocationSumMismatchesQuantity() {
        // Arrange
        BatchAllocation alloc = new BatchAllocation(BATCH_ID, "LOT-001", LocalDate.of(2027, Month.JUNE, 1), 5, BigDecimal.valueOf(5.00));
        SaleItem item = new SaleItem(PRODUCT_ID, "Test", "TST", 10, BigDecimal.TEN, List.of(alloc));

        // Act & Assert
        assertThrows(SaleDomainException.class,
                () -> Sale.create("tenant-1", "user-1", List.of(item)),
                "Mismatched allocation sum should throw");
    }

    @Test
    @DisplayName("Should record price snapshot as submitted")
    void shouldRecordPriceSnapshotAsSubmitted() {
        // Arrange
        BatchAllocation alloc = new BatchAllocation(UUID.randomUUID(), "LOT-001", LocalDate.of(2027, Month.JUNE, 1), 5, BigDecimal.valueOf(5.00));
        SaleItem item = new SaleItem(PRODUCT_ID, "Test", "TST", 5, BigDecimal.valueOf(14.00),
                List.of(alloc));

        // Act
        Sale sale = Sale.create("tenant-1", "user-1", List.of(item));

        // Assert
        assertEquals(0, BigDecimal.valueOf(14.00).compareTo(sale.getItems().get(0).getUnitPrice()),
                "unit price should match submitted value");
    }

    @Test
    @DisplayName("Should void completed sale")
    void shouldVoidCompletedSale() {
        // Arrange
        Sale sale = Sale.create("tenant-1", "user-1", List.of(sampleItem()));

        // Act
        sale.voidSale("Customer request");

        // Assert
        assertEquals(SaleStatus.VOIDED, sale.getStatus(), "status should be VOIDED");
        assertNotNull(sale.getVoidedAt(), "voided timestamp");
        assertEquals("Customer request", sale.getVoidReason(), "void reason");
    }

    @Test
    @DisplayName("Should throw when voiding already voided sale")
    void shouldThrowWhenVoidingAlreadyVoidedSale() {
        // Arrange
        Sale sale = Sale.create("tenant-1", "user-1", List.of(sampleItem()));
        sale.voidSale("First void");

        // Act & Assert
        assertThrows(SaleAlreadyVoidedException.class,
                () -> sale.voidSale("Second void"),
                "Double void should throw SaleAlreadyVoidedException");
    }

    @Test
    @DisplayName("Should throw when voiding with blank reason")
    void shouldThrowWhenVoidingWithBlankReason() {
        // Arrange
        Sale sale = Sale.create("tenant-1", "user-1", List.of(sampleItem()));

        // Act & Assert
        assertThrows(SaleDomainException.class,
                () -> sale.voidSale(""),
                "Blank void reason should throw SaleDomainException");
    }

    @Test
    @DisplayName("Should handle multi-batch allocation")
    void shouldHandleMultiBatchAllocation() {
        // Arrange
        BatchAllocation alloc1 = new BatchAllocation(UUID.randomUUID(), "LOT-A", LocalDate.of(2027, Month.JUNE, 1), 30, BigDecimal.valueOf(5.00));
        BatchAllocation alloc2 = new BatchAllocation(UUID.randomUUID(), "LOT-B", LocalDate.of(2027, Month.JULY, 1), 30, BigDecimal.valueOf(6.00));
        SaleItem item = new SaleItem(PRODUCT_ID, "Test", "TST", 60, BigDecimal.TEN, List.of(alloc1, alloc2));

        // Act
        Sale sale = Sale.create("tenant-1", "user-1", List.of(item));

        // Assert
        assertEquals(60, item.getAllocatedQuantitySum(), "allocated quantity sum");
        assertEquals(2, sale.getItems().get(0).getBatchAllocations().size(), "batch allocations count");
    }

    @Test
    @DisplayName("Should reject batch allocation with null batch id")
    void shouldRejectNullBatchId() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> new BatchAllocation(null, "LOT", LocalDate.now(), 10, BigDecimal.ONE),
                "Null batch id should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should reject batch allocation with negative quantity")
    void shouldRejectNegativeAllocatedQuantity() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> new BatchAllocation(UUID.randomUUID(), "LOT", LocalDate.now(), -1, BigDecimal.ONE),
                "Negative allocated quantity should throw IllegalArgumentException");
    }

    // ─── Multi-item sale lifecycle ──────────────────────────────────

    @Test
    @DisplayName("Should calculate correct total for multi-item sale")
    void shouldCalculateCorrectTotalForMultiItemSale() {
        // Arrange
        UUID prodA = UUID.randomUUID();
        UUID prodB = UUID.randomUUID();
        BatchAllocation alloc1 = new BatchAllocation(UUID.randomUUID(), "LOT-A",
                LocalDate.of(2027, Month.JUNE, 1), 5, BigDecimal.valueOf(5.00));
        BatchAllocation alloc2 = new BatchAllocation(UUID.randomUUID(), "LOT-B",
                LocalDate.of(2027, Month.JULY, 1), 3, BigDecimal.valueOf(6.00));
        SaleItem item1 = new SaleItem(prodA, "Product A", "SKU-A", 5,
                BigDecimal.valueOf(20.00), List.of(alloc1));
        SaleItem item2 = new SaleItem(prodB, "Product B", "SKU-B", 3,
                BigDecimal.valueOf(10.00), List.of(alloc2));

        // Act
        Sale sale = Sale.create("tenant-1", "user-1", List.of(item1, item2));

        // Assert
        // item1 total = 5 * 20.00 = 100.00
        // item2 total = 3 * 10.00 = 30.00
        // grand total = 130.00
        assertEquals(0, BigDecimal.valueOf(130.00).compareTo(sale.getSubtotal()),
                "subtotal should be 130.00");
        assertEquals(0, BigDecimal.valueOf(130.00).compareTo(sale.getTotal()),
                "total should be 130.00");
        assertEquals(2, sale.getItemCount(), "item count");
    }

    @Test
    @DisplayName("Should throw when creating sale with blank tenant")
    void shouldThrowWhenCreatingSaleWithBlankTenant() {
        // Act & Assert
        assertThrows(SaleDomainException.class,
                () -> Sale.create("", "user-1", List.of(sampleItem())),
                "Blank tenant should throw SaleDomainException");
    }

    @Test
    @DisplayName("Should throw when creating sale with blank performedBy")
    void shouldThrowWhenCreatingSaleWithBlankPerformedBy() {
        // Act & Assert
        assertThrows(SaleDomainException.class,
                () -> Sale.create("tenant-1", "", List.of(sampleItem())),
                "Blank performedBy should throw SaleDomainException");
    }

    @Test
    @DisplayName("Should throw when creating sale with null saleDate")
    void shouldThrowWhenCreatingSaleWithNullDate() {
        // Act & Assert
        assertThrows(SaleDomainException.class,
                () -> Sale.create("tenant-1", "user-1", List.of(sampleItem()), null),
                "Null sale date should throw SaleDomainException");
    }

    // ─── Edge case tests ────────────────────────────────────────────

    @Test
    @DisplayName("Should throw when item price is negative")
    void shouldRejectNegativeUnitPrice() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> new SaleItem(PRODUCT_ID, "Test", "TST", 5,
                        BigDecimal.valueOf(-1), List.of(sampleAllocation())),
                "Negative unit price should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should throw when batch allocation lot number is blank")
    void shouldRejectBlankLotNumber() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> new BatchAllocation(UUID.randomUUID(), "", LocalDate.now(), 10, BigDecimal.ONE),
                "Blank lot number should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should throw when batch allocation expiration is null")
    void shouldRejectNullBatchAllocationExpiration() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> new BatchAllocation(UUID.randomUUID(), "LOT", null, 10, BigDecimal.ONE),
                "Null expiration date should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should throw when batch allocation cost is negative")
    void shouldRejectNegativeBatchAllocationCost() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> new BatchAllocation(UUID.randomUUID(), "LOT", LocalDate.now(), 10, BigDecimal.valueOf(-1)),
                "Negative cost should throw IllegalArgumentException");
    }
}
