package com.medistock.productcatalog.infrastructure.rest;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.productcatalog.application.service.RegisterProductUseCase;
import com.medistock.productcatalog.application.service.UpdateProductUseCase;
import com.medistock.productcatalog.application.service.DeactivateProductUseCase;
import com.medistock.productcatalog.application.service.ReactivateProductUseCase;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medistock.productcatalog.application.service.ProductDTO;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import(ProductControllerTest.TestSecurityConfig.class)
@DisplayName("ProductController REST endpoints")
class ProductControllerTest {

    @org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterProductUseCase registerUseCase;

    @MockitoBean
    private UpdateProductUseCase updateUseCase;

    @MockitoBean
    private DeactivateProductUseCase deactivateUseCase;

    @MockitoBean
    private ReactivateProductUseCase reactivateUseCase;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private BatchRepository batchRepository;

    private com.medistock.productcatalog.domain.model.Product sampleProduct;
    private ProductDTO sampleDTO;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("test-tenant");
        sampleProduct = com.medistock.productcatalog.domain.model.Product.register(
                "test-tenant", "Ibuprofeno", "Tablet",
                LocalDate.of(2026, Month.DECEMBER, 31), LocalDate.of(2025, Month.JANUARY, 1));
        sampleDTO = new ProductDTO(
                sampleProduct.getId(), "test-tenant", "Ibuprofeno", null,
                null, null, "Tablet", null, false, false,
                null, "Tablet", null, null, null, true,
                LocalDate.of(2026, Month.DECEMBER, 31), LocalDate.of(2025, Month.JANUARY, 1), 0);
    }

    @Test
    @DisplayName("GET /api/v1/products — should list products")
    void shouldListProducts() throws Exception {
        // Arrange
        var product = com.medistock.productcatalog.domain.model.Product.register(
                "test-tenant", "Ibuprofeno", "Tablet",
                LocalDate.of(2026, Month.DECEMBER, 31), LocalDate.of(2025, Month.JANUARY, 1));

        when(productRepository.findByTenantId(anyString()))
                .thenReturn(List.of(product));

        // Act & Assert
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ibuprofeno"));
    }

    @Test
    @DisplayName("GET /api/v1/products — should return empty list when no products")
    void shouldReturnEmptyListWhenNoProducts() throws Exception {
        // Arrange
        when(productRepository.findByTenantId(anyString()))
                .thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/products?active=true — should filter by active")
    void shouldListProductsByActive() throws Exception {
        // Arrange
        when(productRepository.findByTenantIdAndIsActive(anyString(), eq(true)))
                .thenReturn(List.of(sampleProduct));

        // Act & Assert
        mockMvc.perform(get("/api/v1/products")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ibuprofeno"));
    }

    @Test
    @DisplayName("GET /api/v1/products — should handle null tenant via findAll")
    void shouldListProductsWithNullTenant() throws Exception {
        // Arrange
        TenantContext.clear();
        when(productRepository.findAll())
                .thenReturn(List.of(sampleProduct));

        // Act & Assert
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ibuprofeno"));
    }

    // ─── Get By Id ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/products/{id} — should return product")
    void shouldGetProductById() throws Exception {
        // Arrange
        when(productRepository.findById(sampleProduct.getId()))
                .thenReturn(Optional.of(sampleProduct));

        // Act & Assert
        mockMvc.perform(get("/api/v1/products/{id}", sampleProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ibuprofeno"));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} — should return 404 when not found")
    void shouldReturn404WhenProductNotFound() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id))
                .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound());
    }

    // ─── Create ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/products — should create product")
    void shouldCreateProduct() throws Exception {
        // Arrange
        when(registerUseCase.execute(anyString(), anyString(), anyString(),
                any(), any())).thenReturn(sampleDTO);
        when(updateUseCase.execute(sampleProduct.getId(), "Ibuprofeno", null, null, null,
                "Tablet", null, false, false, null, "Tablet", null, null, null,
                LocalDate.of(2026, 12, 31), LocalDate.of(2025, 1, 1)))
                .thenReturn(sampleDTO);

        // Act & Assert
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Ibuprofeno",
                                    "unit": "Tablet",
                                    "expirationDate": "2026-12-31",
                                    "manufacturingDate": "2025-01-01"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    // ─── Update ─────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/v1/products/{id} — should update product")
    void shouldUpdateProduct() throws Exception {
        // Arrange
        when(updateUseCase.execute(sampleProduct.getId(), "Ibuprofeno Updated", null, null, null,
                "Tablet", null, false, false, null, "Tablet", null, null, null,
                LocalDate.of(2027, 6, 30), LocalDate.of(2025, 1, 1)))
                .thenReturn(sampleDTO);

        // Act & Assert
        mockMvc.perform(put("/api/v1/products/{id}", sampleProduct.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Ibuprofeno Updated",
                                    "unit": "Tablet",
                                    "expirationDate": "2027-06-30",
                                    "manufacturingDate": "2025-01-01"
                                }
                                """))
                .andExpect(status().isOk());
    }

    // ─── Deactivate / Reactivate ────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/products/{id} — should deactivate product")
    void shouldDeactivateProduct() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/v1/products/{id}", sampleProduct.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /api/v1/products/{id}/reactivate — should reactivate product")
    void shouldReactivateProduct() throws Exception {
        // Arrange
        when(reactivateUseCase.execute(sampleProduct.getId()))
                .thenReturn(sampleDTO);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/products/{id}/reactivate", sampleProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ibuprofeno"));
    }
}
