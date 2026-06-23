package com.medistock.dashboard.infrastructure.rest;

import com.medistock.alerts.infrastructure.persistence.SpringDataJpaAlertInstanceRepository;
import com.medistock.crosscutting.security.TenantContext;
import com.medistock.inventory.infrastructure.persistence.SpringDataJpaBatchRepository;
import com.medistock.productcatalog.infrastructure.persistence.SpringDataJpaProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import(DashboardControllerTest.TestSecurityConfig.class)
@DisplayName("DashboardController REST endpoints")
class DashboardControllerTest {

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
    private SpringDataJpaProductRepository productRepo;

    @MockitoBean
    private SpringDataJpaBatchRepository batchRepo;

    @MockitoBean
    private SpringDataJpaAlertInstanceRepository alertRepo;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("test-tenant");
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/summary — should return dashboard summary")
    void shouldReturnDashboardSummary() throws Exception {
        // Arrange
        when(productRepo.countByTenantId(anyString())).thenReturn(50L);
        when(productRepo.countByTenantIdAndIsActive(anyString(), eq(true))).thenReturn(40L);
        when(batchRepo.countByTenantIdAndStatus(anyString(), eq("ACTIVE"))).thenReturn(30L);
        when(alertRepo.countByTenantIdAndStatus(anyString(), eq("ACTIVE"))).thenReturn(5L);
        when(batchRepo.countByTenantIdAndStatusAndExpirationDateBetween(
                anyString(), eq("ACTIVE"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(3L);

        // Act
        var result = mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andReturn();
        var json = objectMapper.readTree(result.getResponse().getContentAsString());

        // Assert
        assertThat(json.get("totalProducts").asLong()).as("totalProducts").isEqualTo(50);
        assertThat(json.get("activeProducts").asLong()).as("activeProducts").isEqualTo(40);
        assertThat(json.get("activeBatches").asLong()).as("activeBatches").isEqualTo(30);
        assertThat(json.get("activeAlerts").asLong()).as("activeAlerts").isEqualTo(5);
        assertThat(json.get("expiringSoonBatches").asLong()).as("expiringSoonBatches").isEqualTo(3);
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/summary — should return zero values when no data")
    void shouldReturnZeroValuesWhenNoData() throws Exception {
        // Arrange
        when(productRepo.countByTenantId(anyString())).thenReturn(0L);
        when(productRepo.countByTenantIdAndIsActive(anyString(), eq(true))).thenReturn(0L);
        when(batchRepo.countByTenantIdAndStatus(anyString(), eq("ACTIVE"))).thenReturn(0L);
        when(alertRepo.countByTenantIdAndStatus(anyString(), eq("ACTIVE"))).thenReturn(0L);
        when(batchRepo.countByTenantIdAndStatusAndExpirationDateBetween(
                anyString(), eq("ACTIVE"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0L);

        // Act
        var result = mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andReturn();
        var json = objectMapper.readTree(result.getResponse().getContentAsString());

        // Assert
        assertThat(json.get("totalProducts").asLong()).as("totalProducts").isEqualTo(0);
        assertThat(json.get("activeProducts").asLong()).as("activeProducts").isEqualTo(0);
        assertThat(json.get("activeBatches").asLong()).as("activeBatches").isEqualTo(0);
        assertThat(json.get("activeAlerts").asLong()).as("activeAlerts").isEqualTo(0);
        assertThat(json.get("expiringSoonBatches").asLong()).as("expiringSoonBatches").isEqualTo(0);
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/summary — should use default tenant when not set")
    void shouldUseDefaultTenantWhenNotSet() throws Exception {
        // Arrange
        TenantContext.clear();

        when(productRepo.countByTenantId(eq("default"))).thenReturn(10L);
        when(productRepo.countByTenantIdAndIsActive(eq("default"), eq(true))).thenReturn(5L);
        when(batchRepo.countByTenantIdAndStatus(eq("default"), eq("ACTIVE"))).thenReturn(3L);
        when(alertRepo.countByTenantIdAndStatus(eq("default"), eq("ACTIVE"))).thenReturn(1L);
        when(batchRepo.countByTenantIdAndStatusAndExpirationDateBetween(
                eq("default"), eq("ACTIVE"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0L);

        // Act
        var result = mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andReturn();
        var json = objectMapper.readTree(result.getResponse().getContentAsString());

        // Assert
        assertThat(json.get("totalProducts").asLong()).as("totalProducts").isEqualTo(10);
    }
}
