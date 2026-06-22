package com.medistock.sales.domain;

import com.medistock.sales.domain.exception.SaleAlreadyVoidedException;
import com.medistock.sales.domain.exception.SaleDomainException;
import com.medistock.sales.domain.model.BatchAllocation;
import com.medistock.sales.domain.model.Sale;
import com.medistock.sales.domain.model.SaleItem;
import com.medistock.sales.domain.model.SaleStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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

    // Scenario 1: Create sale with valid items
    @Test
    void shouldCreateSaleWithValidItems() {
        Sale sale = Sale.create("tenant-1", "user-1", List.of(sampleItem()));
        assertNotNull(sale.getSaleId());
        assertEquals("tenant-1", sale.getTenantId());
        assertEquals(SaleStatus.COMPLETED, sale.getStatus());
        assertEquals(1, sale.getItemCount());
        assertEquals(BigDecimal.valueOf(155.00).stripTrailingZeros(), sale.getTotal().stripTrailingZeros());
        assertEquals(BigDecimal.valueOf(155.00).stripTrailingZeros(), sale.getSubtotal().stripTrailingZeros());
    }

    // SAL-R01: Create with empty items → throws
    @Test
    void shouldThrowWhenCreatingWithEmptyItems() {
        assertThrows(SaleDomainException.class,
                () -> Sale.create("tenant-1", "user-1", List.of()));
    }

    // SAL-R03: Create with quantity = 0 → throws (via SaleItem constructor)
    @Test
    void shouldThrowWhenItemQuantityIsZero() {
        assertThrows(IllegalArgumentException.class,
                () -> new SaleItem(PRODUCT_ID, "Test", "TST", 0, BigDecimal.TEN, List.of(sampleAllocation())));
    }

    // SAL-R11: Total = sum of items
    @Test
    void totalShouldEqualSumOfItemTotals() {
        BatchAllocation alloc1 = new BatchAllocation(UUID.randomUUID(), "LOT-001", LocalDate.of(2027, Month.JUNE, 1), 5, BigDecimal.valueOf(5.00));
        BatchAllocation alloc2 = new BatchAllocation(UUID.randomUUID(), "LOT-002", LocalDate.of(2027, Month.JULY, 1), 3, BigDecimal.valueOf(5.00));

        SaleItem item1 = new SaleItem(PRODUCT_ID, "Ibuprofeno 400mg", "IBU-400", 5, BigDecimal.valueOf(15.50),
                List.of(alloc1));
        SaleItem item2 = new SaleItem(UUID.randomUUID(), "Paracetamol 500mg", "PARA-500", 3, BigDecimal.valueOf(8.90),
                List.of(alloc2));

        Sale sale = Sale.create("tenant-1", "user-1", List.of(item1, item2));
        BigDecimal expected = BigDecimal.valueOf(5 * 15.50 + 3 * 8.90);
        assertEquals(0, sale.getTotal().compareTo(expected));
    }

    // SAL-R07: Sum of batch allocations must equal item quantity
    @Test
    void shouldThrowWhenAllocationSumMismatchesQuantity() {
        BatchAllocation alloc = new BatchAllocation(BATCH_ID, "LOT-001", LocalDate.of(2027, Month.JUNE, 1), 5, BigDecimal.valueOf(5.00));
        SaleItem item = new SaleItem(PRODUCT_ID, "Test", "TST", 10, BigDecimal.TEN, List.of(alloc));

        // The item was created with 10 quantity but only 5 allocated
        // Sale.create should catch this
        assertThrows(SaleDomainException.class,
                () -> Sale.create("tenant-1", "user-1", List.of(item)));
    }

    // SAL-R06: Price snapshot recorded as submitted
    @Test
    void shouldRecordPriceSnapshotAsSubmitted() {
        BatchAllocation alloc = new BatchAllocation(UUID.randomUUID(), "LOT-001", LocalDate.of(2027, 6, 1), 5, BigDecimal.valueOf(5.00));
        SaleItem item = new SaleItem(PRODUCT_ID, "Test", "TST", 5, BigDecimal.valueOf(14.00),
                List.of(alloc));
        Sale sale = Sale.create("tenant-1", "user-1", List.of(item));
        assertEquals(0, BigDecimal.valueOf(14.00).compareTo(sale.getItems().get(0).getUnitPrice()));
    }

    // SAL-R08 / SAL-R09: Void completed sale
    @Test
    void shouldVoidCompletedSale() {
        Sale sale = Sale.create("tenant-1", "user-1", List.of(sampleItem()));
        sale.voidSale("Customer request");
        assertEquals(SaleStatus.VOIDED, sale.getStatus());
        assertNotNull(sale.getVoidedAt());
        assertEquals("Customer request", sale.getVoidReason());
    }

    // SAL-R09: Void already-voided → throws
    @Test
    void shouldThrowWhenVoidingAlreadyVoidedSale() {
        Sale sale = Sale.create("tenant-1", "user-1", List.of(sampleItem()));
        sale.voidSale("First void");
        assertThrows(SaleAlreadyVoidedException.class,
                () -> sale.voidSale("Second void"));
    }

    // SAL-R11: Void with blank reason → throws
    @Test
    void shouldThrowWhenVoidingWithBlankReason() {
        Sale sale = Sale.create("tenant-1", "user-1", List.of(sampleItem()));
        assertThrows(SaleDomainException.class,
                () -> sale.voidSale(""));
    }

    // SAL-R07: Multi-batch allocation sum = item quantity
    @Test
    void shouldHandleMultiBatchAllocation() {
        BatchAllocation alloc1 = new BatchAllocation(UUID.randomUUID(), "LOT-A", LocalDate.of(2027, Month.JUNE, 1), 30, BigDecimal.valueOf(5.00));
        BatchAllocation alloc2 = new BatchAllocation(UUID.randomUUID(), "LOT-B", LocalDate.of(2027, Month.JULY, 1), 30, BigDecimal.valueOf(6.00));
        SaleItem item = new SaleItem(PRODUCT_ID, "Test", "TST", 60, BigDecimal.TEN, List.of(alloc1, alloc2));
        Sale sale = Sale.create("tenant-1", "user-1", List.of(item));
        assertEquals(60, item.getAllocatedQuantitySum());
        assertEquals(2, sale.getItems().get(0).getBatchAllocations().size());
    }

    // BatchAllocation validation
    @Test
    void shouldRejectNullBatchId() {
        assertThrows(IllegalArgumentException.class,
                () -> new BatchAllocation(null, "LOT", LocalDate.now(), 10, BigDecimal.ONE));
    }

    @Test
    void shouldRejectNegativeAllocatedQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> new BatchAllocation(UUID.randomUUID(), "LOT", LocalDate.now(), -1, BigDecimal.ONE));
    }
}
