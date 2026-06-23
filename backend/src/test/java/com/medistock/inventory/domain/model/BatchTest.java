package com.medistock.inventory.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Batch domain entity")
@SuppressWarnings("java:S5778")
class BatchTest {

    private static final String TENANT = "tenant-1";
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final LocalDate EXPIRY = LocalDate.of(2027, Month.JUNE, 18);

    private Batch createValidBatch() {
        return Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100,
                EXPIRY, BigDecimal.valueOf(15.50));
    }

    @Test
    @DisplayName("Should create batch with valid parameters")
    void shouldCreateBatchWithValidParameters() {
        // Arrange
        Batch batch = createValidBatch();

        // Assert
        assertNotNull(batch.getId(), "batch id");
        assertEquals(TENANT, batch.getTenantId(), "tenant id");
        assertEquals(PRODUCT_ID, batch.getProductId(), "product id");
        assertEquals("BATCH-001", batch.getBatchCode(), "batch code");
        assertEquals(100, batch.getInitialQuantity(), "initial quantity");
        assertEquals(100, batch.getCurrentQuantity(), "current quantity");
        assertEquals(BatchStatus.ACTIVE, batch.getStatus(), "status");
    }

    @Test
    @DisplayName("Should throw when tenant is blank")
    void shouldThrowWhenTenantIsBlank() {
        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive("", PRODUCT_ID, "BATCH-001", 100, EXPIRY, BigDecimal.TEN),
                "Blank tenant should throw InventoryDomainException");
    }

    @Test
    @DisplayName("Should throw when product id is null")
    void shouldThrowWhenProductIdIsNull() {
        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive(TENANT, null, "BATCH-001", 100, EXPIRY, BigDecimal.TEN),
                "Null product id should throw InventoryDomainException");
    }

    @Test
    @DisplayName("Should throw when batch code is blank")
    void shouldThrowWhenBatchCodeIsBlank() {
        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive(TENANT, PRODUCT_ID, "", 100, EXPIRY, BigDecimal.TEN),
                "Blank batch code should throw InventoryDomainException");
    }

    @Test
    @DisplayName("Should throw when quantity is zero")
    void shouldThrowWhenQuantityIsZero() {
        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 0, EXPIRY, BigDecimal.TEN),
                "Zero quantity should throw InventoryDomainException");
    }

    @Test
    @DisplayName("Should throw when quantity is negative")
    void shouldThrowWhenQuantityIsNegative() {
        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", -1, EXPIRY, BigDecimal.TEN),
                "Negative quantity should throw InventoryDomainException");
    }

    @Test
    @DisplayName("Should throw when expiration date is null")
    void shouldThrowWhenExpirationDateIsNull() {
        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100, null, BigDecimal.TEN),
                "Null expiration date should throw InventoryDomainException");
    }

    @Test
    @DisplayName("Should throw when cost per unit is null")
    void shouldThrowWhenCostPerUnitIsNull() {
        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100, EXPIRY, null),
                "Null cost per unit should throw InventoryDomainException");
    }

    @Test
    @DisplayName("Should throw when cost per unit is negative")
    void shouldThrowWhenCostPerUnitIsNegative() {
        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100, EXPIRY, BigDecimal.valueOf(-1)),
                "Negative cost per unit should throw InventoryDomainException");
    }

    @Test
    @DisplayName("Should reduce available quantity on allocate")
    void shouldReduceAvailableQuantityOnAllocate() {
        // Arrange
        Batch batch = createValidBatch();

        // Act
        batch.allocate(30);

        // Assert
        assertEquals(70, batch.getCurrentQuantity(), "current quantity after allocation");
        assertEquals(BatchStatus.ACTIVE, batch.getStatus(), "status should remain ACTIVE");
    }

    @Test
    @DisplayName("Should deplete batch when allocating all remaining stock")
    void shouldDepleteBatchWhenAllocateRemaining() {
        // Arrange
        Batch batch = createValidBatch();

        // Act
        batch.allocate(100);

        // Assert
        assertEquals(0, batch.getCurrentQuantity(), "current quantity after full allocation");
        assertEquals(BatchStatus.DEPLETED, batch.getStatus(), "status should be DEPLETED");
    }

    @Test
    @DisplayName("Should throw when allocating more than available quantity")
    void shouldThrowWhenAllocateExceedsAvailable() {
        // Arrange
        Batch batch = createValidBatch();

        // Act & Assert
        assertThrows(InventoryDomainException.class, () -> batch.allocate(101),
                "Allocating beyond available should throw");
    }

    @Test
    @DisplayName("Should throw when allocating zero or negative quantity")
    void shouldThrowWhenAllocateNonPositive() {
        // Arrange
        Batch batch = createValidBatch();

        // Act & Assert
        assertThrows(InventoryDomainException.class, () -> batch.allocate(0),
                "Allocating zero should throw");
        assertThrows(InventoryDomainException.class, () -> batch.allocate(-5),
                "Allocating negative should throw");
    }

    @Test
    @DisplayName("Should throw when allocating from non-active batch")
    void shouldThrowWhenAllocateFromNonActive() {
        // Arrange
        Batch batch = createValidBatch();
        batch.dispose();

        // Act & Assert
        assertThrows(InventoryDomainException.class, () -> batch.allocate(1),
                "Allocating from disposed batch should throw");
    }

    @Test
    @DisplayName("Should quarantine active batch")
    void shouldQuarantineActiveBatch() {
        // Arrange
        Batch batch = createValidBatch();

        // Act
        batch.quarantine();

        // Assert
        assertEquals(BatchStatus.QUARANTINED, batch.getStatus(), "status should be QUARANTINED");
    }

    @Test
    @DisplayName("Should throw when quarantining already quarantined batch")
    void shouldThrowWhenQuarantineAlreadyQuarantined() {
        // Arrange
        Batch batch = createValidBatch();
        batch.quarantine();

        // Act & Assert
        assertThrows(InventoryDomainException.class, batch::quarantine,
                "Double quarantine should throw");
    }

    @Test
    @DisplayName("Should throw when quarantining disposed batch")
    void shouldThrowWhenQuarantineDisposed() {
        // Arrange
        Batch batch = createValidBatch();
        batch.dispose();

        // Act & Assert
        assertThrows(InventoryDomainException.class, batch::quarantine,
                "Quarantining disposed batch should throw");
    }

    @Test
    @DisplayName("Should release batch from quarantine")
    void shouldReleaseFromQuarantine() {
        // Arrange
        Batch batch = createValidBatch();
        batch.quarantine();

        // Act
        batch.releaseFromQuarantine();

        // Assert
        assertEquals(BatchStatus.ACTIVE, batch.getStatus(), "status after release should be ACTIVE");
    }

    @Test
    @DisplayName("Should throw when releasing non-quarantined batch")
    void shouldThrowWhenReleaseNonQuarantined() {
        // Arrange
        Batch batch = createValidBatch();

        // Act & Assert
        assertThrows(InventoryDomainException.class, batch::releaseFromQuarantine,
                "Releasing non-quarantined batch should throw");
    }

    @Test
    @DisplayName("Should dispose active batch")
    void shouldDisposeActiveBatch() {
        // Arrange
        Batch batch = createValidBatch();

        // Act
        batch.dispose();

        // Assert
        assertEquals(BatchStatus.DISPOSED, batch.getStatus(), "status should be DISPOSED");
        assertEquals(0, batch.getCurrentQuantity(), "current quantity should be zero");
    }

    @Test
    @DisplayName("Should throw when disposing already disposed batch")
    void shouldThrowWhenDisposeAlreadyDisposed() {
        // Arrange
        Batch batch = createValidBatch();
        batch.dispose();

        // Act & Assert
        assertThrows(InventoryDomainException.class, batch::dispose,
                "Double dispose should throw");
    }

    @Test
    @DisplayName("Should adjust quantity of batch")
    void shouldAdjustQuantity() {
        // Arrange
        Batch batch = createValidBatch();

        // Act
        batch.adjustQuantity(80);

        // Assert
        assertEquals(80, batch.getCurrentQuantity(), "adjusted quantity");
    }

    @Test
    @DisplayName("Should deplete batch when adjusting to zero")
    void shouldDepleteOnAdjustToZero() {
        // Arrange
        Batch batch = createValidBatch();

        // Act
        batch.adjustQuantity(0);

        // Assert
        assertEquals(0, batch.getCurrentQuantity(), "quantity after adjust to zero");
        assertEquals(BatchStatus.DEPLETED, batch.getStatus(), "status should be DEPLETED");
    }

    @Test
    @DisplayName("Should throw when adjusting to negative quantity")
    void shouldThrowWhenAdjustNegative() {
        // Arrange
        Batch batch = createValidBatch();

        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                batch.adjustQuantity(-1), "Adjusting to negative should throw");
    }

    @Test
    @DisplayName("Should throw when adjusting disposed batch")
    void shouldThrowWhenAdjustDisposed() {
        // Arrange
        Batch batch = createValidBatch();
        batch.dispose();

        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                batch.adjustQuantity(50), "Adjusting disposed batch should throw");
    }

    @Test
    @DisplayName("Should throw when adjusting exceeds initial quantity")
    void shouldThrowWhenAdjustExceedsInitialQuantity() {
        // Arrange
        Batch batch = createValidBatch();

        // Act & Assert
        assertThrows(InventoryDomainException.class, () ->
                batch.adjustQuantity(101), "Adjusting above initial quantity should throw");
    }

    @Test
    @DisplayName("Should mark batch as expired")
    void shouldMarkExpired() {
        // Arrange
        Batch batch = createValidBatch();

        // Act
        batch.markExpired();

        // Assert
        assertEquals(BatchStatus.EXPIRED, batch.getStatus(), "status should be EXPIRED");
    }

    @Test
    @DisplayName("Should throw when disposing an expired batch")
    void shouldThrowWhenExpiredThenDispose() {
        // Arrange
        Batch batch = createValidBatch();
        batch.markExpired();

        // Act & Assert
        assertThrows(InventoryDomainException.class, batch::dispose,
                "Disposing expired batch should throw");
    }

    // ─── State Machine: QUARANTINED transitions ──────────────────────

    @Test
    @DisplayName("Should throw when allocating from QUARANTINED batch")
    void shouldThrowWhenAllocateFromQuarantined() {
        // Arrange
        Batch batch = createValidBatch();
        batch.quarantine();

        // Act & Assert
        assertThrows(InventoryDomainException.class, () -> batch.allocate(1),
                "Allocating from quarantined batch should throw");
    }

    @Test
    @DisplayName("Should throw when allocating from DEPLETED batch")
    void shouldThrowWhenAllocateFromDepleted() {
        // Arrange
        Batch batch = createValidBatch();
        batch.allocate(100);

        // Act & Assert
        assertThrows(InventoryDomainException.class, () -> batch.allocate(1),
                "Allocating from depleted batch should throw");
    }

    @Test
    @DisplayName("Should throw when adjusting a DEPLETED batch")
    void shouldThrowWhenAdjustDepleted() {
        // Arrange
        Batch batch = createValidBatch();
        batch.allocate(100);

        // Act & Assert
        assertThrows(InventoryDomainException.class, () -> batch.adjustQuantity(50),
                "Adjusting depleted batch should throw");
    }

    // ─── State Machine: DISPOSED transitions ─────────────────────────

    @Test
    @DisplayName("Should throw when marking a DISPOSED batch as expired")
    void shouldThrowWhenMarkExpiredOnDisposed() {
        // Arrange
        Batch batch = createValidBatch();
        batch.dispose();

        // Act & Assert
        assertThrows(InventoryDomainException.class, batch::markExpired,
                "Marking disposed batch as expired should throw");
    }

    @Test
    @DisplayName("Should throw when restoring quantity on a DISPOSED batch")
    void shouldThrowWhenRestoreQuantityOnDisposed() {
        // Arrange
        Batch batch = createValidBatch();
        batch.dispose();

        // Act & Assert
        assertThrows(InventoryDomainException.class, () -> batch.restoreQuantity(10),
                "Restoring quantity on disposed batch should throw");
    }

    // ─── State Machine: DEPLETED restore transitions ─────────────────

    @Test
    @DisplayName("Should restore DEPLETED batch back to ACTIVE when quantity restored")
    void shouldRestoreDepletedBatchToActive() {
        // Arrange
        Batch batch = createValidBatch();
        batch.allocate(100);

        // Act
        batch.restoreQuantity(30);

        // Assert
        assertEquals(30, batch.getCurrentQuantity(), "current quantity after restore");
        assertEquals(BatchStatus.ACTIVE, batch.getStatus(), "status should be ACTIVE after restore");
    }

    @Test
    @DisplayName("Should restore DEPLETED batch with minimal quantity back to ACTIVE")
    void shouldRestoreMinimalQuantityToActivate() {
        // Arrange
        Batch batch = createValidBatch();
        batch.allocate(100);

        // Act
        batch.restoreQuantity(1);

        // Assert
        assertEquals(1, batch.getCurrentQuantity(), "current quantity after restore");
        assertEquals(BatchStatus.ACTIVE, batch.getStatus(), "status should become ACTIVE");
    }

    @Test
    @DisplayName("Should restore quantity to EXPIRED batch")
    void shouldRestoreQuantityToExpiredBatch() {
        // Arrange
        Batch batch = createValidBatch();
        batch.markExpired();
        int before = batch.getCurrentQuantity();

        // Act
        batch.restoreQuantity(20);

        // Assert
        assertEquals(before + 20, batch.getCurrentQuantity(), "quantity should increase");
        assertEquals(BatchStatus.EXPIRED, batch.getStatus(), "status should remain EXPIRED");
    }

    @Test
    @DisplayName("Should throw when restoring non-positive quantity")
    void shouldThrowWhenRestoreNonPositive() {
        // Arrange
        Batch batch = createValidBatch();

        // Act & Assert
        assertThrows(InventoryDomainException.class, () -> batch.restoreQuantity(0),
                "Restoring zero should throw");
        assertThrows(InventoryDomainException.class, () -> batch.restoreQuantity(-5),
                "Restoring negative should throw");
    }

    // ─── Adjust edge cases ──────────────────────────────────────────

    @Test
    @DisplayName("Should dispose QUARANTINED batch directly")
    void shouldDisposeQuarantinedBatch() {
        // Arrange
        Batch batch = createValidBatch();
        batch.quarantine();

        // Act
        batch.dispose();

        // Assert
        assertEquals(BatchStatus.DISPOSED, batch.getStatus(), "status should be DISPOSED");
        assertEquals(0, batch.getCurrentQuantity(), "current quantity should be zero");
    }

}
