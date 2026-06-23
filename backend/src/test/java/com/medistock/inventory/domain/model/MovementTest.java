package com.medistock.inventory.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Movement domain entity")
class MovementTest {

    private static final String TENANT = "tenant-1";
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Test
    @DisplayName("Should create valid movement with all fields")
    void shouldCreateValidMovement() {
        // Arrange & Act
        Movement movement = Movement.create(TENANT, BATCH_ID, PRODUCT_ID,
                MovementType.RECEIPT, 100, 0, 100,
                "Initial receipt", "user-1");

        // Assert
        assertNotNull(movement.getId(), "movement id");
        assertEquals(TENANT, movement.getTenantId(), "tenant id");
        assertEquals(BATCH_ID, movement.getBatchId(), "batch id");
        assertEquals(PRODUCT_ID, movement.getProductId(), "product id");
        assertEquals(MovementType.RECEIPT, movement.getType(), "type");
        assertEquals(100, movement.getQuantity(), "quantity");
        assertEquals(0, movement.getPreviousQuantity(), "previous quantity");
        assertEquals(100, movement.getNewQuantity(), "new quantity");
        assertEquals("Initial receipt", movement.getReason(), "reason");
        assertEquals("user-1", movement.getPerformedBy(), "performed by");
        assertNotNull(movement.getCreatedAt(), "created timestamp");
    }

    @Test
    @DisplayName("Should throw when tenant is blank")
    void shouldThrowWhenTenantIsBlank() {
        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                Movement.create("", BATCH_ID, PRODUCT_ID, MovementType.RECEIPT,
                        100, 0, 100, "test", "user"),
                "Blank tenant should throw InventoryDomainException");
    }

    @Test
    @DisplayName("Should throw when batch id is null")
    void shouldThrowWhenBatchIdIsNull() {
        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                Movement.create(TENANT, null, PRODUCT_ID, MovementType.RECEIPT,
                        100, 0, 100, "test", "user"),
                "Null batch id should throw InventoryDomainException");
    }

    @Test
    @DisplayName("Should throw when product id is null")
    void shouldThrowWhenProductIdIsNull() {
        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                Movement.create(TENANT, BATCH_ID, null, MovementType.RECEIPT,
                        100, 0, 100, "test", "user"),
                "Null product id should throw InventoryDomainException");
    }

    @Test
    @DisplayName("Should throw when type is null")
    void shouldThrowWhenTypeIsNull() {
        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                Movement.create(TENANT, BATCH_ID, PRODUCT_ID, null,
                        100, 0, 100, "test", "user"),
                "Null type should throw InventoryDomainException");
    }

    @Test
    @DisplayName("Should throw when quantity is zero and stock changes")
    void shouldThrowWhenQuantityIsZeroAndStockChanges() {
        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                Movement.create(TENANT, BATCH_ID, PRODUCT_ID, MovementType.ADJUSTMENT,
                        0, 100, 80, "test", "user"),
                "Zero quantity with stock change should throw");
    }

    @Test
    @DisplayName("Should allow zero quantity for status-only changes")
    void shouldAllowZeroQuantityForStatusChange() {
        // Arrange & Act
        Movement movement = Movement.create(TENANT, BATCH_ID, PRODUCT_ID,
                MovementType.QUARANTINE_IN, 0, 100, 100,
                "Quarantined", "user-1");

        // Assert
        assertEquals(0, movement.getQuantity(), "quantity");
        assertEquals(100, movement.getPreviousQuantity(), "previous quantity");
        assertEquals(100, movement.getNewQuantity(), "new quantity");
    }

    @Test
    @DisplayName("Should throw when quantities are inconsistent")
    void shouldThrowWhenQuantitiesAreInconsistent() {
        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                Movement.create(TENANT, BATCH_ID, PRODUCT_ID, MovementType.ADJUSTMENT,
                        -10, 100, 100, "test", "user"),
                "Inconsistent quantities should throw");
    }

    @Test
    @DisplayName("Should be immutable — no setter methods allowed")
    void shouldBeImmutable() {
        // Arrange
        Movement movement = Movement.create(TENANT, BATCH_ID, PRODUCT_ID,
                MovementType.RECEIPT, 100, 0, 100,
                "Initial receipt", "user-1");

        // Act & Assert
        assertThrows(NoSuchMethodException.class, () ->
                movement.getClass().getMethod("setQuantity", int.class),
                "Movement should not expose setQuantity");
    }

    @Test
    @DisplayName("Should create movement with negative quantity for stock reduction")
    void shouldCreateMovementWithNegativeQuantityForReduction() {
        // Arrange & Act
        Movement movement = Movement.create(TENANT, BATCH_ID, PRODUCT_ID,
                MovementType.ADJUSTMENT, -20, 100, 80,
                "Stock reduction", "user-1");

        // Assert
        assertEquals(-20, movement.getQuantity(), "negative quantity");
        assertEquals(100, movement.getPreviousQuantity(), "previous quantity");
        assertEquals(80, movement.getNewQuantity(), "new quantity");
    }
}
