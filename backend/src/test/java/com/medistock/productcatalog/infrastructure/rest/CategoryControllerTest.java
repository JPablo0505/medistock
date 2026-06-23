package com.medistock.productcatalog.infrastructure.rest;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.productcatalog.domain.model.Category;
import com.medistock.productcatalog.domain.port.CategoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@Import(CategoryControllerTest.TestSecurityConfig.class)
@DisplayName("CategoryController REST endpoints")
class CategoryControllerTest {

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

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("test-tenant");
    }

    @Test
    @DisplayName("POST /api/v1/categories — should create category (201)")
    void shouldCreateCategory() throws Exception {
        // Arrange
        var savedCategory = Category.create("test-tenant", "Analgésicos", "Pain relief");
        when(categoryRepository.save(any())).thenReturn(savedCategory);

        CreateCategoryRequest request = new CreateCategoryRequest("Analgésicos", "Pain relief");

        // Act & Assert
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Analgésicos"))
                .andExpect(jsonPath("$.description").value("Pain relief"));
    }

    @Test
    @DisplayName("POST /api/v1/categories — should return 400 when name is blank")
    void shouldReturn400WhenCreatingCategoryWithBlankName() throws Exception {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest("", "desc");

        // Act & Assert
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/categories — should list categories")
    void shouldListCategories() throws Exception {
        // Arrange
        Category cat1 = Category.create("test-tenant", "Analgésicos", "Pain relief");
        Category cat2 = Category.create("test-tenant", "Antibióticos", "Antibiotics");
        when(categoryRepository.findByTenantId(anyString())).thenReturn(List.of(cat1, cat2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Analgésicos"))
                .andExpect(jsonPath("$[1].name").value("Antibióticos"));
    }

    @Test
    @DisplayName("GET /api/v1/categories — should return empty list when no categories")
    void shouldReturnEmptyListWhenNoCategories() throws Exception {
        // Arrange
        when(categoryRepository.findByTenantId(anyString())).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/categories — should use default tenant when not set")
    void shouldUseDefaultTenantWhenNotSet() throws Exception {
        // Arrange
        TenantContext.clear();
        Category cat = Category.create("default", "Genéricos", "Generic drugs");
        when(categoryRepository.findByTenantId("default")).thenReturn(List.of(cat));

        // Act & Assert
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Genéricos"));
    }
}
