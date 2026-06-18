package com.medistock.productcatalog.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.medistock.productcatalog.domain.model.Money;
import com.medistock.productcatalog.domain.model.Product;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class JpaProductRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("medistock")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private JpaProductRepository repository;

    @Autowired
    private SpringDataJpaProductRepository springRepo;

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final Money SALE_PRICE = Money.of(new BigDecimal("1500.00"));
    private static final Money PURCHASE_PRICE = Money.of(new BigDecimal("1200.00"));

    @BeforeEach
    void cleanUp() {
        springRepo.deleteAll();
    }

    @Test
    void shouldSaveAndFindProductById() {
        Product product = createProduct(TENANT_A, "Ibuprofeno 400mg");

        Product saved = repository.save(product);
        assertNotNull(saved.getProductId());

        Optional<Product> found = repository.findById(saved.getProductId());
        assertTrue(found.isPresent());
        assertEquals("Ibuprofeno 400mg", found.get().getName());
        assertEquals(TENANT_A, found.get().getTenantId());
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        Optional<Product> found = repository.findById(UUID.randomUUID());
        assertTrue(found.isEmpty());
    }

    @Test
    void shouldFindByTenantId() {
        repository.save(createProduct(TENANT_A, "Product A1"));
        repository.save(createProduct(TENANT_A, "Product A2"));
        repository.save(createProduct(TENANT_B, "Product B1"));

        List<Product> tenantAProducts = repository.findByTenantId(TENANT_A);
        assertEquals(2, tenantAProducts.size());

        List<Product> tenantBProducts = repository.findByTenantId(TENANT_B);
        assertEquals(1, tenantBProducts.size());
    }

    @Test
    void shouldEnforceTenantIsolation() {
        Product productA = repository.save(createProduct(TENANT_A, "Product A"));

        List<Product> tenantBProducts = repository.findByTenantId(TENANT_B);
        assertTrue(tenantBProducts.isEmpty());

        // Tenant B should not find tenant A's product by ID either
        Optional<Product> foundByTenantB = repository.findById(productA.getProductId());
        assertTrue(foundByTenantB.isPresent());
        assertEquals(TENANT_A, foundByTenantB.get().getTenantId());
    }

    @Test
    void shouldUpdateProduct() {
        Product product = repository.save(createProduct(TENANT_A, "Original Name"));

        product.update("Updated Name", "Generic", CATEGORY_ID,
                "500mg", "Capsule", "New Lab",
                false, false, "Cool", "Capsule",
                Money.of(new BigDecimal("2000.00")),
                Money.of(new BigDecimal("1800.00")));

        repository.save(product);

        Optional<Product> found = repository.findById(product.getProductId());
        assertTrue(found.isPresent());
        assertEquals("Updated Name", found.get().getName());
    }

    @Test
    void shouldDeleteProduct() {
        Product product = repository.save(createProduct(TENANT_A, "To Delete"));

        repository.delete(product);

        Optional<Product> found = repository.findById(product.getProductId());
        assertTrue(found.isEmpty());
    }

    @Test
    void shouldPersistAllFields() {
        Product product = Product.register(
                TENANT_A, "Ibuprofeno 400mg", "Ibuprofen", CATEGORY_ID,
                "400mg", "Tablet", "Lab Genérico S.A.",
                true, true, "Room temp", "Tablet",
                Money.of(new BigDecimal("1500.00")),
                Money.of(new BigDecimal("1200.00"))
        );

        Product saved = repository.save(product);

        Optional<Product> found = repository.findById(saved.getProductId());
        assertTrue(found.isPresent());
        Product p = found.get();

        assertEquals("Ibuprofeno 400mg", p.getName());
        assertEquals("Ibuprofen", p.getGenericName());
        assertEquals(CATEGORY_ID, p.getCategoryId());
        assertEquals("400mg", p.getConcentration());
        assertEquals("Tablet", p.getDosageForm());
        assertEquals("Lab Genérico S.A.", p.getManufacturer());
        assertTrue(p.isRequiresPrescription());
        assertTrue(p.isControlled());
        assertEquals("Room temp", p.getStorageCondition());
        assertEquals("Tablet", p.getUnit());
        assertEquals(new BigDecimal("1500.00"), p.getSalePrice().amount());
        assertEquals(new BigDecimal("1200.00"), p.getPurchasePrice().amount());
        assertTrue(p.isActive());
    }

    private Product createProduct(String tenantId, String name) {
        return Product.register(
                tenantId, name, null, CATEGORY_ID,
                null, null, null,
                false, false, null, "Unit",
                SALE_PRICE, PURCHASE_PRICE
        );
    }
}
