package com.medistock.productcatalog.infrastructure.rest;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medistock.crosscutting.security.TenantContext;
import com.medistock.productcatalog.application.service.DeactivateProductUseCase;
import com.medistock.productcatalog.application.service.ProductDTO;
import com.medistock.productcatalog.application.service.ReactivateProductUseCase;
import com.medistock.productcatalog.application.service.RegisterProductUseCase;
import com.medistock.productcatalog.application.service.UpdateProductUseCase;
import com.medistock.productcatalog.domain.model.Money;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.port.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = ProductController.class,
    excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
    }
)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RegisterProductUseCase registerProductUseCase;

    @MockitoBean
    private UpdateProductUseCase updateProductUseCase;

    @MockitoBean
    private DeactivateProductUseCase deactivateProductUseCase;

    @MockitoBean
    private ReactivateProductUseCase reactivateProductUseCase;

    @MockitoBean
    private ProductRepository productRepository;

    private static final String TENANT_ID = "test-tenant";
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createProduct_shouldReturn201() throws Exception {
        ProductDTO dto = buildDTO();
        when(registerProductUseCase.execute(
                eq(TENANT_ID), eq("Ibuprofeno 400mg"), eq("Ibuprofen"),
                eq(CATEGORY_ID), eq("400mg"), eq("Tablet"),
                eq("Lab Genérico"), eq(false), eq(false),
                eq("Room temp"), eq("Tablet"),
                eq(new BigDecimal("1500.00")), eq(new BigDecimal("1200.00"))
        )).thenReturn(dto);

        String requestBody = """
                {
                    "name": "Ibuprofeno 400mg",
                    "genericName": "Ibuprofen",
                    "categoryId": "%s",
                    "concentration": "400mg",
                    "dosageForm": "Tablet",
                    "manufacturer": "Lab Genérico",
                    "requiresPrescription": false,
                    "isControlled": false,
                    "storageCondition": "Room temp",
                    "unit": "Tablet",
                    "salePrice": 1500.00,
                    "purchasePrice": 1200.00
                }
                """.formatted(CATEGORY_ID.toString());

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(dto.id().toString()))
                .andExpect(jsonPath("$.name").value("Ibuprofeno 400mg"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void createProduct_shouldReturn400WhenNameBlank() throws Exception {
        String requestBody = """
                {
                    "name": "",
                    "unit": "Tablet",
                    "salePrice": 1000,
                    "purchasePrice": 800
                }
                """;

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_shouldReturn409WhenNoTenant() throws Exception {
        TenantContext.clear();

        String requestBody = """
                {
                    "name": "Test",
                    "unit": "Tablet",
                    "salePrice": 1000,
                    "purchasePrice": 800
                }
                """;

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }

    @Test
    void listProducts_shouldReturn200() throws Exception {
        Product product = createTestProduct();
        when(productRepository.findByTenantId(TENANT_ID))
                .thenReturn(List.of(product));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ibuprofeno 400mg"))
                .andExpect(jsonPath("$[0].id").value(PRODUCT_ID.toString()));
    }

    @Test
    void listProducts_shouldReturnEmptyWhenNoTenant() throws Exception {
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getProduct_shouldReturn200() throws Exception {
        Product product = createTestProduct();
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        mockMvc.perform(get("/api/v1/products/{id}", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ibuprofeno 400mg"))
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()));
    }

    @Test
    void getProduct_shouldReturn404WhenNotFound() throws Exception {
        UUID notFoundId = UUID.randomUUID();
        when(productRepository.findById(notFoundId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/products/{id}", notFoundId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void updateProduct_shouldReturn200() throws Exception {
        ProductDTO dto = buildDTO();
        when(updateProductUseCase.execute(
                eq(PRODUCT_ID), eq("Updated Name"), eq("Generic"),
                eq(CATEGORY_ID), eq("500mg"), eq("Capsule"),
                eq("New Lab"), eq(false), eq(false),
                eq("Cool"), eq("Capsule"),
                eq(new BigDecimal("2000.00")), eq(new BigDecimal("1800.00"))
        )).thenReturn(dto);

        String requestBody = """
                {
                    "name": "Updated Name",
                    "genericName": "Generic",
                    "categoryId": "%s",
                    "concentration": "500mg",
                    "dosageForm": "Capsule",
                    "manufacturer": "New Lab",
                    "requiresPrescription": false,
                    "isControlled": false,
                    "storageCondition": "Cool",
                    "unit": "Capsule",
                    "salePrice": 2000.00,
                    "purchasePrice": 1800.00
                }
                """.formatted(CATEGORY_ID.toString());

        mockMvc.perform(put("/api/v1/products/{id}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ibuprofeno 400mg"));
    }

    @Test
    void deactivateProduct_shouldReturn200() throws Exception {
        ProductDTO dto = new ProductDTO(
                PRODUCT_ID, TENANT_ID, "Ibuprofeno 400mg", null, CATEGORY_ID,
                null, null, null, false, false, null, "Tablet",
                BigDecimal.ZERO, BigDecimal.ZERO, false, NOW, NOW
        );
        when(deactivateProductUseCase.execute(PRODUCT_ID)).thenReturn(dto);

        mockMvc.perform(delete("/api/v1/products/{id}", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void reactivateProduct_shouldReturn200() throws Exception {
        ProductDTO dto = new ProductDTO(
                PRODUCT_ID, TENANT_ID, "Ibuprofeno 400mg", null, CATEGORY_ID,
                null, null, null, false, false, null, "Tablet",
                BigDecimal.ZERO, BigDecimal.ZERO, true, NOW, NOW
        );
        when(reactivateProductUseCase.execute(PRODUCT_ID)).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/products/{id}/reactivate", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
    }

    // --- Helpers ---

    private ProductDTO buildDTO() {
        return new ProductDTO(
                PRODUCT_ID, TENANT_ID, "Ibuprofeno 400mg", "Ibuprofen", CATEGORY_ID,
                "400mg", "Tablet", "Lab Genérico",
                false, false, "Room temp", "Tablet",
                new BigDecimal("1500.00"), new BigDecimal("1200.00"),
                true, NOW, NOW
        );
    }

    private Product createTestProduct() {
        return Product.reconstitute(
                PRODUCT_ID, TENANT_ID, "Ibuprofeno 400mg", null, CATEGORY_ID,
                null, null, null, false, false, null, "Tablet",
                Money.of(BigDecimal.ZERO), Money.of(BigDecimal.ZERO),
                true, NOW, NOW
        );
    }
}
