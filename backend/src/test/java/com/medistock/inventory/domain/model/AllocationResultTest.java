package com.medistock.inventory.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AllocationResult value object")
class AllocationResultTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_A = UUID.randomUUID();
    private static final UUID BATCH_B = UUID.randomUUID();
    private static final LocalDate EXPIRY = LocalDate.of(2027, Month.JUNE, 1);

    @Test
    @DisplayName("Should create from lines with full allocation")
    void shouldCreateFromLinesFullAllocation() {
        // Arrange
        List<AllocationLine> lines = List.of(
                new AllocationLine(BATCH_A, "LOT-A", 30, BigDecimal.valueOf(5.00),
                        BigDecimal.valueOf(150.00), EXPIRY),
                new AllocationLine(BATCH_B, "LOT-B", 20, BigDecimal.valueOf(6.00),
                        BigDecimal.valueOf(120.00), EXPIRY.plusMonths(1))
        );

        // Act
        AllocationResult result = AllocationResult.fromLines(PRODUCT_ID, 50, lines);

        // Assert
        assertEquals(PRODUCT_ID, result.productId(), "product id");
        assertEquals(50, result.requestedQuantity(), "requested quantity");
        assertEquals(50, result.totalAllocated(), "total allocated");
        assertTrue(result.fullyAllocated(), "should be fully allocated");
        assertEquals(0, BigDecimal.valueOf(270.00).compareTo(result.totalCost()), "total cost = 150 + 120");
        assertEquals(2, result.lines().size(), "allocation lines count");
    }

    @Test
    @DisplayName("Should create from lines with partial allocation")
    void shouldCreateFromLinesPartialAllocation() {
        // Arrange
        List<AllocationLine> lines = List.of(
                new AllocationLine(BATCH_A, "LOT-A", 30, BigDecimal.valueOf(5.00),
                        BigDecimal.valueOf(150.00), EXPIRY)
        );

        // Act
        AllocationResult result = AllocationResult.fromLines(PRODUCT_ID, 100, lines);

        // Assert
        assertFalse(result.fullyAllocated(), "should NOT be fully allocated");
        assertEquals(30, result.totalAllocated(), "total allocated");
    }

    @Test
    @DisplayName("Should create none result")
    void shouldCreateNoneResult() {
        // Act
        AllocationResult result = AllocationResult.none(PRODUCT_ID, 100);

        // Assert
        assertEquals(0, result.totalAllocated(), "total allocated should be 0");
        assertFalse(result.fullyAllocated(), "should not be fully allocated");
        assertTrue(result.lines().isEmpty(), "lines should be empty");
        assertEquals(BigDecimal.ZERO, result.totalCost(), "total cost should be zero");
    }

    @Test
    @DisplayName("Should throw on null product id in fromLines")
    void shouldThrowOnNullProductIdInFromLines() {
        assertThrows(InventoryDomainException.class,
                () -> AllocationResult.fromLines(null, 10, List.of()),
                "Null product id should throw");
    }

    @Test
    @DisplayName("Should throw on non-positive requested quantity in fromLines")
    void shouldThrowOnNonPositiveRequestedQuantityInFromLines() {
        assertThrows(InventoryDomainException.class,
                () -> AllocationResult.fromLines(PRODUCT_ID, 0, List.of()),
                "Zero requested quantity should throw");
    }

    @Test
    @DisplayName("Should throw on null product id in none")
    void shouldThrowOnNullProductIdInNone() {
        assertThrows(InventoryDomainException.class,
                () -> AllocationResult.none(null, 10),
                "Null product id should throw");
    }

    @Test
    @DisplayName("Should throw on non-positive requested quantity in none")
    void shouldThrowOnNonPositiveRequestedQuantityInNone() {
        assertThrows(InventoryDomainException.class,
                () -> AllocationResult.none(PRODUCT_ID, 0),
                "Zero requested quantity should throw");
    }

    @Test
    @DisplayName("Should implement equals based on value")
    void shouldImplementValueEquality() {
        // Arrange
        List<AllocationLine> lines = List.of(
                new AllocationLine(BATCH_A, "LOT-A", 30, BigDecimal.valueOf(5.00),
                        BigDecimal.valueOf(150.00), EXPIRY)
        );
        AllocationResult a = AllocationResult.fromLines(PRODUCT_ID, 30, lines);
        AllocationResult b = AllocationResult.fromLines(PRODUCT_ID, 30, lines);

        // Assert
        assertEquals(a, b, "equal results should be equal");
        assertEquals(a.hashCode(), b.hashCode(), "equal results should have same hashCode");
    }

    @Test
    @DisplayName("Should handle empty lines in fromLines")
    void shouldHandleEmptyLinesInFromLines() {
        // Act
        AllocationResult result = AllocationResult.fromLines(PRODUCT_ID, 10, List.of());

        // Assert
        assertEquals(0, result.totalAllocated(), "total allocated");
        assertFalse(result.fullyAllocated(), "should not be fully allocated");
        assertTrue(result.lines().isEmpty(), "lines should be empty");
    }
}
