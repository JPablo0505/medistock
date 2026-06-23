package com.medistock.inventory.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AllocationLine record")
@SuppressWarnings("java:S5778")
class AllocationLineTest {

    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final LocalDate EXPIRY = LocalDate.of(2027, Month.JUNE, 1);

    @Test
    @DisplayName("Should create allocation line with all fields")
    void shouldCreateAllocationLineWithAllFields() {
        // Arrange & Act
        AllocationLine line = new AllocationLine(
                BATCH_ID, "LOT-001", 50,
                BigDecimal.valueOf(10.00), BigDecimal.valueOf(500.00), EXPIRY);

        // Assert
        assertEquals(BATCH_ID, line.batchId(), "batch id");
        assertEquals("LOT-001", line.batchCode(), "batch code");
        assertEquals(50, line.quantityAllocated(), "quantity allocated");
        assertEquals(0, BigDecimal.valueOf(10.00).compareTo(line.unitCost()), "unit cost");
        assertEquals(0, BigDecimal.valueOf(500.00).compareTo(line.totalCost()), "total cost");
        assertEquals(EXPIRY, line.expirationDate(), "expiration date");
    }

    @Test
    @DisplayName("Should create allocation line with legacy constructor")
    void shouldCreateWithLegacyConstructor() {
        // Arrange & Act
        AllocationLine line = new AllocationLine(
                BATCH_ID, "LOT-001", 50,
                BigDecimal.valueOf(10.00), BigDecimal.valueOf(500.00));

        // Assert
        assertEquals(BATCH_ID, line.batchId(), "batch id");
        assertEquals(50, line.quantityAllocated(), "quantity allocated");
        assertNull(line.expirationDate(), "expiration date should be null");
    }

    @Test
    @DisplayName("Should throw on null batch id")
    void shouldThrowOnNullBatchId() {
        assertThrows(InventoryDomainException.class,
                () -> new AllocationLine(null, "LOT", 10, BigDecimal.TEN, BigDecimal.valueOf(100), EXPIRY),
                "Null batch id should throw");
    }

    @Test
    @DisplayName("Should throw on non-positive quantity")
    void shouldThrowOnNonPositiveQuantity() {
        assertThrows(InventoryDomainException.class,
                () -> new AllocationLine(BATCH_ID, "LOT", 0, BigDecimal.TEN, BigDecimal.ZERO, EXPIRY),
                "Zero quantity should throw");
    }

    @Test
    @DisplayName("Should throw on null unit cost")
    void shouldThrowOnNullUnitCost() {
        assertThrows(InventoryDomainException.class,
                () -> new AllocationLine(BATCH_ID, "LOT", 10, null, BigDecimal.ZERO, EXPIRY),
                "Null unit cost should throw");
    }

    @Test
    @DisplayName("Should implement equals based on record components")
    void shouldImplementRecordEquality() {
        // Arrange
        var a = new AllocationLine(BATCH_ID, "LOT-A", 30, BigDecimal.valueOf(5.00),
                BigDecimal.valueOf(150.00), EXPIRY);
        var b = new AllocationLine(BATCH_ID, "LOT-A", 30, BigDecimal.valueOf(5.00),
                BigDecimal.valueOf(150.00), EXPIRY);

        // Assert
        assertEquals(a, b, "equal lines should be equal");
        assertEquals(a.hashCode(), b.hashCode(), "equal lines should have same hashCode");
    }
}
