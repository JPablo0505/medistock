package com.medistock.productcatalog.domain.model;

import com.medistock.shared.domain.AggregateRoot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Product domain entity")
class ProductTest {

    private static final LocalDate EXP = LocalDate.of(2026, Month.DECEMBER, 31);
    private static final LocalDate MFR = LocalDate.of(2025, Month.JANUARY, 1);

    private Product registerDefault() {
        return Product.register("tenant-1", "Ibuprofeno 600mg", "Tablet", EXP, MFR);
    }

    @Test
    @DisplayName("Should register product with valid parameters")
    void shouldRegisterProduct() {
        // Arrange & Act
        Product product = registerDefault();

        // Assert
        assertNotNull(product.getId(), "product id");
        assertEquals("tenant-1", product.getTenantId(), "tenant id");
        assertEquals("Ibuprofeno 600mg", product.getName(), "name");
        assertEquals("Tablet", product.getUnit(), "unit");
        assertTrue(product.isActive(), "active flag");
    }

    @Test
    @DisplayName("Should reject blank name when registering product")
    void shouldRejectBlankName() {
        // Act & Assert
        assertThrows(ProductDomainException.class,
                () -> Product.register("tenant-1", "  ", "Tablet", EXP, MFR),
                "Blank name should throw ProductDomainException");
    }

    @Test
    @DisplayName("Should reject null name when registering product")
    void shouldRejectNullName() {
        // Act & Assert
        assertThrows(ProductDomainException.class,
                () -> Product.register("tenant-1", null, "Tablet", EXP, MFR),
                "Null name should throw ProductDomainException");
    }

    @Test
    @DisplayName("Should reject blank unit when registering product")
    void shouldRejectBlankUnit() {
        // Act & Assert
        assertThrows(ProductDomainException.class,
                () -> Product.register("tenant-1", "Ibuprofeno", "  ", EXP, MFR),
                "Blank unit should throw ProductDomainException");
    }

    @Test
    @DisplayName("Should reject null unit when registering product")
    void shouldRejectNullUnit() {
        // Act & Assert
        assertThrows(ProductDomainException.class,
                () -> Product.register("tenant-1", "Ibuprofeno", null, EXP, MFR),
                "Null unit should throw ProductDomainException");
    }

    @Test
    @DisplayName("Should reject blank tenant id when registering product")
    void shouldRejectBlankTenantId() {
        // Act & Assert
        assertThrows(ProductDomainException.class,
                () -> Product.register("", "Ibuprofeno", "Tablet", EXP, MFR),
                "Blank tenant id should throw ProductDomainException");
    }

    @Test
    @DisplayName("Should reject null expiration date when registering product")
    void shouldRejectNullExpirationDate() {
        // Act & Assert
        assertThrows(ProductDomainException.class,
                () -> Product.register("tenant-1", "Ibuprofeno", "Tablet", null, MFR),
                "Null expiration date should throw ProductDomainException");
    }

    @Test
    @DisplayName("Should reject null manufacturing date when registering product")
    void shouldRejectNullManufacturingDate() {
        // Act & Assert
        assertThrows(ProductDomainException.class,
                () -> Product.register("tenant-1", "Ibuprofeno", "Tablet", EXP, null),
                "Null manufacturing date should throw ProductDomainException");
    }

    @Test
    @DisplayName("Should update product with valid data")
    void shouldUpdateProduct() {
        // Arrange
        Product product = registerDefault();
        UUID catId = UUID.randomUUID();

        // Act
        product.update("Ibuprofeno 800mg", "Generic Ibu", catId,
                "800mg", "Tablet", "Genfar",
                false, false, "Room temperature",
                "Tablet", BigDecimal.valueOf(80), BigDecimal.valueOf(40),
                null, EXP, MFR);

        // Assert
        assertEquals("Ibuprofeno 800mg", product.getName(), "updated name");
        assertEquals("Generic Ibu", product.getGenericName(), "generic name");
        assertEquals(catId, product.getCategoryId(), "category id");
        assertEquals("800mg", product.getConcentration(), "concentration");
        assertEquals("Genfar", product.getManufacturer(), "manufacturer");
        assertEquals(0, BigDecimal.valueOf(80).compareTo(product.getSalePrice().amount()), "sale price");
        assertEquals(0, BigDecimal.valueOf(40).compareTo(product.getPurchasePrice().amount()), "purchase price");
    }

    @Test
    @DisplayName("Should reject update with blank name")
    void shouldRejectUpdateWithBlankName() {
        // Arrange
        Product product = registerDefault();

        // Act & Assert
        assertThrows(ProductDomainException.class,
                () -> product.update("  ", null, null,
                        null, null, null,
                        false, false, null,
                        "Tablet", BigDecimal.TEN, BigDecimal.ZERO,
                        null, EXP, MFR),
                "Update with blank name should throw");
    }

    @Test
    @DisplayName("Should reject update with negative sale price")
    void shouldRejectUpdateWithNegativeSalePrice() {
        // Arrange
        Product product = registerDefault();

        // Act & Assert
        assertThrows(ProductDomainException.class,
                () -> product.update("Ibuprofeno", null, null,
                        null, null, null,
                        false, false, null,
                        "Tablet", BigDecimal.valueOf(-1), BigDecimal.ZERO,
                        null, EXP, MFR),
                "Update with negative sale price should throw");
    }

    @Test
    @DisplayName("Should deactivate active product")
    void shouldDeactivateProduct() {
        // Arrange
        Product product = registerDefault();

        // Act
        product.deactivate();

        // Assert
        assertFalse(product.isActive(), "product should be inactive");
    }

    @Test
    @DisplayName("Should not deactivate already inactive product")
    void shouldNotDeactivateAlreadyInactiveProduct() {
        // Arrange
        Product product = registerDefault();
        product.deactivate();

        // Act & Assert
        assertThrows(ProductDomainException.class, product::deactivate,
                "Deactivating already inactive product should throw");
    }

    @Test
    @DisplayName("Should reactivate inactive product")
    void shouldReactivateProduct() {
        // Arrange
        Product product = registerDefault();
        product.deactivate();

        // Act
        product.reactivate();

        // Assert
        assertTrue(product.isActive(), "product should be active after reactivate");
    }

    @Test
    @DisplayName("Should not reactivate already active product")
    void shouldNotReactivateAlreadyActiveProduct() {
        // Arrange
        Product product = registerDefault();

        // Act & Assert
        assertThrows(ProductDomainException.class, product::reactivate,
                "Reactivating already active product should throw");
    }

    @Test
    @DisplayName("Should implement AggregateRoot interface")
    void shouldImplementAggregateRoot() {
        // Arrange
        Product product = registerDefault();

        // Assert
        assertInstanceOf(AggregateRoot.class, product,
                "Product should implement AggregateRoot");
    }
}
