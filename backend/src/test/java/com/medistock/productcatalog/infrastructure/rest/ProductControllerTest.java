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

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("test-tenant");
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
}
