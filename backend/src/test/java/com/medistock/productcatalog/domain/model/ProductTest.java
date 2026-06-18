package com.medistock.productcatalog.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final String NAME = "Ibuprofeno 400mg";
    private static final String GENERIC_NAME = "Ibuprofen";
    private static final String CONCENTRATION = "400mg";
    private static final String DOSAGE_FORM = "Tablet";
    private static final String MANUFACTURER = "Lab Genérico S.A.";
    private static final String STORAGE_CONDITION = "Room temperature";
    private static final String UNIT = "Tablet";
    private static final Money SALE_PRICE = Money.of(new BigDecimal("1500.00"));
    private static final Money PURCHASE_PRICE = Money.of(new BigDecimal("1200.00"));

    @Test
    void shouldRegisterProductWithValidData() {
        Product product = Product.register(
                TENANT_ID.toString(), NAME, GENERIC_NAME, CATEGORY_ID,
                CONCENTRATION, DOSAGE_FORM, MANUFACTURER,
                false, false, STORAGE_CONDITION, UNIT,
                SALE_PRICE, PURCHASE_PRICE);

        assertNotNull(product.getProductId());
        assertEquals(TENANT_ID.toString(), product.getTenantId());
        assertEquals(NAME, product.getName());
        assertEquals(GENERIC_NAME, product.getGenericName());
        assertEquals(CATEGORY_ID, product.getCategoryId());
        assertEquals(CONCENTRATION, product.getConcentration());
        assertEquals(DOSAGE_FORM, product.getDosageForm());
        assertEquals(MANUFACTURER, product.getManufacturer());
        assertFalse(product.isRequiresPrescription());
        assertFalse(product.isControlled());
        assertEquals(STORAGE_CONDITION, product.getStorageCondition());
        assertEquals(UNIT, product.getUnit());
        assertEquals(SALE_PRICE, product.getSalePrice());
        assertEquals(PURCHASE_PRICE, product.getPurchasePrice());
        assertTrue(product.isActive());
        assertNotNull(product.getCreatedAt());
        assertNotNull(product.getUpdatedAt());
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(ProductDomainException.class, () -> Product.register(
                TENANT_ID.toString(), "  ", GENERIC_NAME, CATEGORY_ID,
                CONCENTRATION, DOSAGE_FORM, MANUFACTURER,
                false, false, STORAGE_CONDITION, UNIT,
                SALE_PRICE, PURCHASE_PRICE));
    }

    @Test
    void shouldRejectNullName() {
        assertThrows(ProductDomainException.class, () -> Product.register(
                TENANT_ID.toString(), null, GENERIC_NAME, CATEGORY_ID,
                CONCENTRATION, DOSAGE_FORM, MANUFACTURER,
                false, false, STORAGE_CONDITION, UNIT,
                SALE_PRICE, PURCHASE_PRICE));
    }

    @Test
    void shouldRejectBlankTenantId() {
        assertThrows(ProductDomainException.class, () -> Product.register(
                "  ", NAME, GENERIC_NAME, CATEGORY_ID,
                CONCENTRATION, DOSAGE_FORM, MANUFACTURER,
                false, false, STORAGE_CONDITION, UNIT,
                SALE_PRICE, PURCHASE_PRICE));
    }

    @Test
    void shouldRejectNullCategoryId() {
        assertThrows(ProductDomainException.class, () -> Product.register(
                TENANT_ID.toString(), NAME, GENERIC_NAME, null,
                CONCENTRATION, DOSAGE_FORM, MANUFACTURER,
                false, false, STORAGE_CONDITION, UNIT,
                SALE_PRICE, PURCHASE_PRICE));
    }

    @Test
    void shouldRejectNullUnit() {
        assertThrows(ProductDomainException.class, () -> Product.register(
                TENANT_ID.toString(), NAME, GENERIC_NAME, CATEGORY_ID,
                CONCENTRATION, DOSAGE_FORM, MANUFACTURER,
                false, false, STORAGE_CONDITION, null,
                SALE_PRICE, PURCHASE_PRICE));
    }

    @Test
    void shouldRejectBlankUnit() {
        assertThrows(ProductDomainException.class, () -> Product.register(
                TENANT_ID.toString(), NAME, GENERIC_NAME, CATEGORY_ID,
                CONCENTRATION, DOSAGE_FORM, MANUFACTURER,
                false, false, STORAGE_CONDITION, "  ",
                SALE_PRICE, PURCHASE_PRICE));
    }

    @Test
    void shouldRejectNullSalePrice() {
        assertThrows(ProductDomainException.class, () -> Product.register(
                TENANT_ID.toString(), NAME, GENERIC_NAME, CATEGORY_ID,
                CONCENTRATION, DOSAGE_FORM, MANUFACTURER,
                false, false, STORAGE_CONDITION, UNIT,
                null, PURCHASE_PRICE));
    }

    @Test
    void shouldRejectNullPurchasePrice() {
        assertThrows(ProductDomainException.class, () -> Product.register(
                TENANT_ID.toString(), NAME, GENERIC_NAME, CATEGORY_ID,
                CONCENTRATION, DOSAGE_FORM, MANUFACTURER,
                false, false, STORAGE_CONDITION, UNIT,
                SALE_PRICE, null));
    }

    @Test
    void shouldAllowNullOptionalFields() {
        Product product = Product.register(
                TENANT_ID.toString(), NAME, null, CATEGORY_ID,
                null, null, null,
                false, false, null, UNIT,
                SALE_PRICE, PURCHASE_PRICE);

        assertNull(product.getGenericName());
        assertNull(product.getConcentration());
        assertNull(product.getDosageForm());
        assertNull(product.getManufacturer());
        assertNull(product.getStorageCondition());
    }

    @Test
    void shouldUpdateProduct() {
        Product product = createDefaultProduct();
        UUID newCategoryId = UUID.randomUUID();
        Money newPrice = Money.of(new BigDecimal("2000.00"));

        product.update("Paracetamol 500mg", "Paracetamol", newCategoryId,
                "500mg", "Capsule", "New Lab",
                false, false, "Cool storage", "Capsule",
                newPrice, PURCHASE_PRICE);

        assertEquals("Paracetamol 500mg", product.getName());
        assertEquals("Paracetamol", product.getGenericName());
        assertEquals(newCategoryId, product.getCategoryId());
        assertEquals("500mg", product.getConcentration());
        assertEquals("Capsule", product.getDosageForm());
        assertEquals("New Lab", product.getManufacturer());
        assertFalse(product.isRequiresPrescription());
        assertFalse(product.isControlled());
        assertEquals("Cool storage", product.getStorageCondition());
        assertEquals("Capsule", product.getUnit());
        assertEquals(newPrice, product.getSalePrice());
        assertEquals(PURCHASE_PRICE, product.getPurchasePrice());
        assertNotNull(product.getUpdatedAt());
    }

    @Test
    void shouldRejectUpdateWithBlankName() {
        Product product = createDefaultProduct();
        assertThrows(ProductDomainException.class, () ->
                product.update("  ", GENERIC_NAME, CATEGORY_ID,
                        CONCENTRATION, DOSAGE_FORM, MANUFACTURER,
                        false, false, STORAGE_CONDITION, UNIT,
                        SALE_PRICE, PURCHASE_PRICE));
    }

    @Test
    void shouldDeactivateProduct() {
        Product product = createDefaultProduct();
        assertTrue(product.isActive());

        product.deactivate();
        assertFalse(product.isActive());
    }

    @Test
    void shouldAllowDeactivatingAlreadyDeactivatedProduct() {
        Product product = createDefaultProduct();
        product.deactivate();
        product.deactivate(); // Should not throw
        assertFalse(product.isActive());
    }

    @Test
    void shouldReactivateProduct() {
        Product product = createDefaultProduct();
        product.deactivate();
        assertFalse(product.isActive());

        product.reactivate();
        assertTrue(product.isActive());
    }

    @Test
    void shouldAllowReactivatingAlreadyActiveProduct() {
        Product product = createDefaultProduct();
        product.reactivate(); // Should not throw
        assertTrue(product.isActive());
    }

    @Test
    void shouldRegisterWithDefaultActiveAndPrices() {
        Product product = Product.register(
                TENANT_ID.toString(), NAME, GENERIC_NAME, CATEGORY_ID,
                CONCENTRATION, DOSAGE_FORM, MANUFACTURER,
                false, false, STORAGE_CONDITION, UNIT,
                SALE_PRICE, PURCHASE_PRICE);

        assertTrue(product.isActive());
        assertNotNull(product.getSalePrice());
        assertNotNull(product.getPurchasePrice());
    }

    @Test
    void shouldImplementAggregateRoot() {
        Product product = createDefaultProduct();
        assertTrue(product instanceof com.medistock.shared.domain.AggregateRoot);
    }

    private Product createDefaultProduct() {
        return Product.register(
                TENANT_ID.toString(), NAME, GENERIC_NAME, CATEGORY_ID,
                CONCENTRATION, DOSAGE_FORM, MANUFACTURER,
                false, false, STORAGE_CONDITION, UNIT,
                SALE_PRICE, PURCHASE_PRICE);
    }
}
