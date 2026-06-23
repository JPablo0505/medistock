package com.medistock.inventory.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StockProjection value object")
class StockProjectionTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final LocalDate NEAREST_EXPIRY = LocalDate.of(2026, Month.JULY, 15);

    @Test
    @DisplayName("Should create projection with all fields")
    void shouldCreateProjectionWithAllFields() {
        // Arrange & Act
        StockProjection projection = new StockProjection(
                PRODUCT_ID, 500, 350, 50, 40, 60, 10, NEAREST_EXPIRY);

        // Assert
        assertEquals(PRODUCT_ID, projection.getProductId(), "product id");
        assertEquals(500, projection.getTotalQuantity(), "total quantity");
        assertEquals(350, projection.getAvailableQuantity(), "available quantity");
        assertEquals(50, projection.getQuarantinedQuantity(), "quarantined quantity");
        assertEquals(40, projection.getExpiredQuantity(), "expired quantity");
        assertEquals(60, projection.getDisposedQuantity(), "disposed quantity");
        assertEquals(10, projection.getTotalBatches(), "total batches");
        assertEquals(NEAREST_EXPIRY, projection.getNearestExpiryDate(), "nearest expiry");
    }

    @Test
    @DisplayName("Should handle zero quantities for all statuses")
    void shouldHandleZeroQuantities() {
        // Arrange & Act
        StockProjection projection = new StockProjection(
                PRODUCT_ID, 0, 0, 0, 0, 0, 0, null);

        // Assert
        assertEquals(0, projection.getTotalQuantity(), "total quantity");
        assertEquals(0, projection.getAvailableQuantity(), "available quantity");
        assertEquals(0, projection.getQuarantinedQuantity(), "quarantined quantity");
        assertEquals(0, projection.getExpiredQuantity(), "expired quantity");
        assertEquals(0, projection.getDisposedQuantity(), "disposed quantity");
        assertEquals(0, projection.getTotalBatches(), "total batches");
        assertNull(projection.getNearestExpiryDate(), "nearest expiry should be null");
    }

    @Test
    @DisplayName("Should preserve correct component sums")
    void shouldPreserveCorrectComponentSums() {
        // Given total = 500, available + quarantined + expired + disposed = 350 + 50 + 40 + 60 = 500
        StockProjection projection = new StockProjection(
                PRODUCT_ID, 500, 350, 50, 40, 60, 5, NEAREST_EXPIRY);

        int componentSum = projection.getAvailableQuantity()
                + projection.getQuarantinedQuantity()
                + projection.getExpiredQuantity()
                + projection.getDisposedQuantity();

        assertEquals(projection.getTotalQuantity(), componentSum,
                "component quantities should sum to total");
    }

    @Test
    @DisplayName("Should handle empty batch set (single batch per status)")
    void shouldHandleRepresentativeMix() {
        // Simulating a realistic product with 4 batches in different states
        StockProjection projection = new StockProjection(
                PRODUCT_ID,
                220,    // total: 100 + 50 + 30 + 40
                100,    // ACTIVE
                50,     // QUARANTINED
                30,     // EXPIRED
                40,     // DISPOSED
                4,      // batches
                LocalDate.of(2026, Month.AUGUST, 1));

        assertEquals(220, projection.getTotalQuantity());
        assertTrue(projection.getAvailableQuantity() > 0, "should have available stock");
        assertTrue(projection.getNearestExpiryDate() != null, "should have a nearest expiry");
    }
}
