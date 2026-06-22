package com.medistock.inventory.domain.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MovementTest {

    private static final String TENANT = "tenant-1";
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Test
    void shouldCreateValidMovement() {
        Movement movement = Movement.create(TENANT, BATCH_ID, PRODUCT_ID,
                MovementType.RECEIPT, 100, 0, 100,
                "Initial receipt", "user-1");

        assertNotNull(movement.getId());
        assertEquals(TENANT, movement.getTenantId());
        assertEquals(BATCH_ID, movement.getBatchId());
        assertEquals(PRODUCT_ID, movement.getProductId());
        assertEquals(MovementType.RECEIPT, movement.getType());
        assertEquals(100, movement.getQuantity());
        assertEquals(0, movement.getPreviousQuantity());
        assertEquals(100, movement.getNewQuantity());
        assertEquals("Initial receipt", movement.getReason());
        assertEquals("user-1", movement.getPerformedBy());
        assertNotNull(movement.getCreatedAt());
    }

    @Test
    void shouldThrowWhenTenantIsBlank() {
        assertThrows(InventoryDomainException.class, () ->
                Movement.create("", BATCH_ID, PRODUCT_ID, MovementType.RECEIPT,
                        100, 0, 100, "test", "user"));
    }

    @Test
    void shouldThrowWhenBatchIdIsNull() {
        assertThrows(InventoryDomainException.class, () ->
                Movement.create(TENANT, null, PRODUCT_ID, MovementType.RECEIPT,
                        100, 0, 100, "test", "user"));
    }

    @Test
    void shouldThrowWhenProductIdIsNull() {
        assertThrows(InventoryDomainException.class, () ->
                Movement.create(TENANT, BATCH_ID, null, MovementType.RECEIPT,
                        100, 0, 100, "test", "user"));
    }

    @Test
    void shouldThrowWhenTypeIsNull() {
        assertThrows(InventoryDomainException.class, () ->
                Movement.create(TENANT, BATCH_ID, PRODUCT_ID, null,
                        100, 0, 100, "test", "user"));
    }

    @Test
    void shouldThrowWhenQuantityIsZeroAndStockChanges() {
        assertThrows(InventoryDomainException.class, () ->
                Movement.create(TENANT, BATCH_ID, PRODUCT_ID, MovementType.ADJUSTMENT,
                        0, 100, 80, "test", "user"));
    }

    @Test
    void shouldAllowZeroQuantityForStatusChange() {
        Movement movement = Movement.create(TENANT, BATCH_ID, PRODUCT_ID,
                MovementType.QUARANTINE_IN, 0, 100, 100,
                "Quarantined", "user-1");
        assertEquals(0, movement.getQuantity());
        assertEquals(100, movement.getPreviousQuantity());
        assertEquals(100, movement.getNewQuantity());
    }

    @Test
    void shouldThrowWhenQuantitiesAreInconsistent() {
        assertThrows(InventoryDomainException.class, () ->
                Movement.create(TENANT, BATCH_ID, PRODUCT_ID, MovementType.ADJUSTMENT,
                        -10, 100, 100, "test", "user"));
        // -10 + 100 != 100
    }

    @Test
    void shouldBeImmutable() {
        Movement movement = Movement.create(TENANT, BATCH_ID, PRODUCT_ID,
                MovementType.RECEIPT, 100, 0, 100,
                "Initial receipt", "user-1");

        assertThrows(NoSuchMethodException.class, () ->
                movement.getClass().getMethod("setQuantity", int.class));
    }

    @Test
    void shouldCreateMovementWithNegativeQuantityForReduction() {
        Movement movement = Movement.create(TENANT, BATCH_ID, PRODUCT_ID,
                MovementType.ADJUSTMENT, -20, 100, 80,
                "Stock reduction", "user-1");
        assertEquals(-20, movement.getQuantity());
        assertEquals(100, movement.getPreviousQuantity());
        assertEquals(80, movement.getNewQuantity());
    }
}
