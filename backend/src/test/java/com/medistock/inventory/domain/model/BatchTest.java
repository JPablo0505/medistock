package com.medistock.inventory.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BatchTest {

    private static final String TENANT = "tenant-1";
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final LocalDate EXPIRY = LocalDate.of(2027, Month.JUNE, 18);

    private Batch createValidBatch() {
        return Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100,
                EXPIRY, BigDecimal.valueOf(15.50));
    }

    @Test
    void shouldCreateBatchWithValidParameters() {
        Batch batch = createValidBatch();
        assertNotNull(batch.getId());
        assertEquals(TENANT, batch.getTenantId());
        assertEquals(PRODUCT_ID, batch.getProductId());
        assertEquals("BATCH-001", batch.getBatchCode());
        assertEquals(100, batch.getInitialQuantity());
        assertEquals(100, batch.getCurrentQuantity());
        assertEquals(BatchStatus.ACTIVE, batch.getStatus());
    }

    @Test
    void shouldThrowWhenTenantIsBlank() {
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive("", PRODUCT_ID, "BATCH-001", 100, EXPIRY, BigDecimal.TEN));
    }

    @Test
    void shouldThrowWhenProductIdIsNull() {
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive(TENANT, null, "BATCH-001", 100, EXPIRY, BigDecimal.TEN));
    }

    @Test
    void shouldThrowWhenBatchCodeIsBlank() {
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive(TENANT, PRODUCT_ID, "", 100, EXPIRY, BigDecimal.TEN));
    }

    @Test
    void shouldThrowWhenQuantityIsZero() {
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 0, EXPIRY, BigDecimal.TEN));
    }

    @Test
    void shouldThrowWhenQuantityIsNegative() {
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", -1, EXPIRY, BigDecimal.TEN));
    }

    @Test
    void shouldThrowWhenExpirationDateIsNull() {
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100, null, BigDecimal.TEN));
    }

    @Test
    void shouldThrowWhenCostPerUnitIsNull() {
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100, EXPIRY, null));
    }

    @Test
    void shouldThrowWhenCostPerUnitIsNegative() {
        assertThrows(InventoryDomainException.class, () ->
                Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100, EXPIRY, BigDecimal.valueOf(-1)));
    }

    @Test
    void shouldReduceAvailableQuantityOnAllocate() {
        Batch batch = createValidBatch();
        batch.allocate(30);
        assertEquals(70, batch.getCurrentQuantity());
        assertEquals(BatchStatus.ACTIVE, batch.getStatus());
    }

    @Test
    void shouldDepleteBatchWhenAllocateRemaining() {
        Batch batch = createValidBatch();
        batch.allocate(100);
        assertEquals(0, batch.getCurrentQuantity());
        assertEquals(BatchStatus.DEPLETED, batch.getStatus());
    }

    @Test
    void shouldThrowWhenAllocateExceedsAvailable() {
        Batch batch = createValidBatch();
        assertThrows(InventoryDomainException.class, () -> batch.allocate(101));
    }

    @Test
    void shouldThrowWhenAllocateNonPositive() {
        Batch batch = createValidBatch();
        assertThrows(InventoryDomainException.class, () -> batch.allocate(0));
        assertThrows(InventoryDomainException.class, () -> batch.allocate(-5));
    }

    @Test
    void shouldThrowWhenAllocateFromNonActive() {
        Batch batch = createValidBatch();
        batch.dispose();
        assertThrows(InventoryDomainException.class, () -> batch.allocate(1));
    }

    @Test
    void shouldQuarantineActiveBatch() {
        Batch batch = createValidBatch();
        batch.quarantine();
        assertEquals(BatchStatus.QUARANTINED, batch.getStatus());
    }

    @Test
    void shouldThrowWhenQuarantineAlreadyQuarantined() {
        Batch batch = createValidBatch();
        batch.quarantine();
        assertThrows(InventoryDomainException.class, batch::quarantine);
    }

    @Test
    void shouldThrowWhenQuarantineDisposed() {
        Batch batch = createValidBatch();
        batch.dispose();
        assertThrows(InventoryDomainException.class, batch::quarantine);
    }

    @Test
    void shouldReleaseFromQuarantine() {
        Batch batch = createValidBatch();
        batch.quarantine();
        batch.releaseFromQuarantine();
        assertEquals(BatchStatus.ACTIVE, batch.getStatus());
    }

    @Test
    void shouldThrowWhenReleaseNonQuarantined() {
        Batch batch = createValidBatch();
        assertThrows(InventoryDomainException.class, batch::releaseFromQuarantine);
    }

    @Test
    void shouldDisposeActiveBatch() {
        Batch batch = createValidBatch();
        batch.dispose();
        assertEquals(BatchStatus.DISPOSED, batch.getStatus());
        assertEquals(0, batch.getCurrentQuantity());
    }

    @Test
    void shouldThrowWhenDisposeAlreadyDisposed() {
        Batch batch = createValidBatch();
        batch.dispose();
        assertThrows(InventoryDomainException.class, batch::dispose);
    }

    @Test
    void shouldAdjustQuantity() {
        Batch batch = createValidBatch();
        batch.adjustQuantity(80);
        assertEquals(80, batch.getCurrentQuantity());
    }

    @Test
    void shouldDepleteOnAdjustToZero() {
        Batch batch = createValidBatch();
        batch.adjustQuantity(0);
        assertEquals(0, batch.getCurrentQuantity());
        assertEquals(BatchStatus.DEPLETED, batch.getStatus());
    }

    @Test
    void shouldThrowWhenAdjustNegative() {
        Batch batch = createValidBatch();
        assertThrows(InventoryDomainException.class, () ->
                batch.adjustQuantity(-1));
    }

    @Test
    void shouldThrowWhenAdjustDisposed() {
        Batch batch = createValidBatch();
        batch.dispose();
        assertThrows(InventoryDomainException.class, () ->
                batch.adjustQuantity(50));
    }

    @Test
    void shouldThrowWhenAdjustExceedsInitialQuantity() {
        Batch batch = createValidBatch();
        assertThrows(InventoryDomainException.class, () ->
                batch.adjustQuantity(101));
    }

    @Test
    void shouldMarkExpired() {
        Batch batch = createValidBatch();
        batch.markExpired();
        assertEquals(BatchStatus.EXPIRED, batch.getStatus());
    }

    @Test
    void shouldThrowWhenExpiredThenDispose() {
        Batch batch = createValidBatch();
        batch.markExpired();
        assertThrows(InventoryDomainException.class, batch::dispose);
    }
}
