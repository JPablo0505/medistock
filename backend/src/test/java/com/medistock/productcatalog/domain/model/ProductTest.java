package com.medistock.productcatalog.domain.model;

import com.medistock.shared.domain.AggregateRoot;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProductTest {

    @Test
    void shouldRegisterProduct() {
        Product product = Product.register("tenant-1", "Ibuprofeno 600mg", "Tablet");
        assertNotNull(product.getId());
        assertEquals("tenant-1", product.getTenantId());
        assertEquals("Ibuprofeno 600mg", product.getName());
        assertEquals("Tablet", product.getUnit());
        assertTrue(product.isActive());
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(ProductDomainException.class,
                () -> Product.register("tenant-1", "  ", "Tablet"));
    }

    @Test
    void shouldRejectNullName() {
        assertThrows(ProductDomainException.class,
                () -> Product.register("tenant-1", null, "Tablet"));
    }

    @Test
    void shouldRejectBlankUnit() {
        assertThrows(ProductDomainException.class,
                () -> Product.register("tenant-1", "Ibuprofeno", "  "));
    }

    @Test
    void shouldRejectNullUnit() {
        assertThrows(ProductDomainException.class,
                () -> Product.register("tenant-1", "Ibuprofeno", null));
    }

    @Test
    void shouldRejectBlankTenantId() {
        assertThrows(ProductDomainException.class,
                () -> Product.register("", "Ibuprofeno", "Tablet"));
    }

    @Test
    void shouldUpdateProduct() {
        Product product = Product.register("tenant-1", "Ibuprofeno", "Tablet");
        UUID catId = UUID.randomUUID();

        product.update("Ibuprofeno 800mg", "Generic Ibu", catId,
                "800mg", "Tablet", "Genfar",
                false, false, "Room temperature",
                "Tablet", BigDecimal.valueOf(80), BigDecimal.valueOf(40));

        assertEquals("Ibuprofeno 800mg", product.getName());
        assertEquals("Generic Ibu", product.getGenericName());
        assertEquals(catId, product.getCategoryId());
        assertEquals("800mg", product.getConcentration());
        assertEquals("Genfar", product.getManufacturer());
        assertEquals(0, BigDecimal.valueOf(80).compareTo(product.getSalePrice().amount()));
        assertEquals(0, BigDecimal.valueOf(40).compareTo(product.getPurchasePrice().amount()));
    }

    @Test
    void shouldRejectUpdateWithBlankName() {
        Product product = Product.register("tenant-1", "Ibuprofeno", "Tablet");
        assertThrows(ProductDomainException.class,
                () -> product.update("  ", null, null,
                        null, null, null,
                        false, false, null,
                        "Tablet", BigDecimal.TEN, BigDecimal.ZERO));
    }

    @Test
    void shouldRejectUpdateWithNegativeSalePrice() {
        Product product = Product.register("tenant-1", "Ibuprofeno", "Tablet");
        assertThrows(ProductDomainException.class,
                () -> product.update("Ibuprofeno", null, null,
                        null, null, null,
                        false, false, null,
                        "Tablet", BigDecimal.valueOf(-1), BigDecimal.ZERO));
    }

    @Test
    void shouldDeactivateProduct() {
        Product product = Product.register("tenant-1", "Ibuprofeno", "Tablet");
        product.deactivate();
        assertFalse(product.isActive());
    }

    @Test
    void shouldNotDeactivateAlreadyInactiveProduct() {
        Product product = Product.register("tenant-1", "Ibuprofeno", "Tablet");
        product.deactivate();
        assertThrows(ProductDomainException.class, product::deactivate);
    }

    @Test
    void shouldReactivateProduct() {
        Product product = Product.register("tenant-1", "Ibuprofeno", "Tablet");
        product.deactivate();
        product.reactivate();
        assertTrue(product.isActive());
    }

    @Test
    void shouldNotReactivateAlreadyActiveProduct() {
        Product product = Product.register("tenant-1", "Ibuprofeno", "Tablet");
        assertThrows(ProductDomainException.class, product::reactivate);
    }

    @Test
    void shouldImplementAggregateRoot() {
        Product product = Product.register("tenant-1", "Test", "Unit");
        assertInstanceOf(AggregateRoot.class, product);
    }
}
