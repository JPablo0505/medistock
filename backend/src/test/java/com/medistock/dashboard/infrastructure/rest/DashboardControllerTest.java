package com.medistock.dashboard.infrastructure.rest;

import com.medistock.alerts.infrastructure.persistence.SpringDataJpaAlertInstanceRepository;
import com.medistock.crosscutting.security.TenantContext;
import com.medistock.inventory.infrastructure.persistence.SpringDataJpaBatchRepository;
import com.medistock.productcatalog.infrastructure.persistence.SpringDataJpaProductRepository;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import(DashboardControllerTest.TestSecurityConfig.class)
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
    void shouldReturnDashboardSummary() throws Exception {
        when(productRepo.countByTenantId(anyString())).thenReturn(50L);
        when(productRepo.countByTenantIdAndIsActive(anyString(), eq(true))).thenReturn(40L);
        when(batchRepo.countByTenantIdAndStatus(anyString(), eq("ACTIVE"))).thenReturn(30L);
        when(alertRepo.countByTenantIdAndStatus(anyString(), eq("ACTIVE"))).thenReturn(5L);
        when(batchRepo.countByTenantIdAndStatusAndExpirationDateBetween(
                anyString(), eq("ACTIVE"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(3L);

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").value(50))
                .andExpect(jsonPath("$.activeProducts").value(40))
                .andExpect(jsonPath("$.activeBatches").value(30))
                .andExpect(jsonPath("$.activeAlerts").value(5))
                .andExpect(jsonPath("$.expiringSoonBatches").value(3));
    }

    @Test
    void shouldReturnZeroValuesWhenNoData() throws Exception {
        when(productRepo.countByTenantId(anyString())).thenReturn(0L);
        when(productRepo.countByTenantIdAndIsActive(anyString(), eq(true))).thenReturn(0L);
        when(batchRepo.countByTenantIdAndStatus(anyString(), eq("ACTIVE"))).thenReturn(0L);
        when(alertRepo.countByTenantIdAndStatus(anyString(), eq("ACTIVE"))).thenReturn(0L);
        when(batchRepo.countByTenantIdAndStatusAndExpirationDateBetween(
                anyString(), eq("ACTIVE"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0L);

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").value(0))
                .andExpect(jsonPath("$.activeProducts").value(0))
                .andExpect(jsonPath("$.activeBatches").value(0))
                .andExpect(jsonPath("$.activeAlerts").value(0))
                .andExpect(jsonPath("$.expiringSoonBatches").value(0));
    }

    @Test
    void shouldUseDefaultTenantWhenNotSet() throws Exception {
        TenantContext.clear();

        when(productRepo.countByTenantId(eq("default"))).thenReturn(10L);
        when(productRepo.countByTenantIdAndIsActive(eq("default"), eq(true))).thenReturn(5L);
        when(batchRepo.countByTenantIdAndStatus(eq("default"), eq("ACTIVE"))).thenReturn(3L);
        when(alertRepo.countByTenantIdAndStatus(eq("default"), eq("ACTIVE"))).thenReturn(1L);
        when(batchRepo.countByTenantIdAndStatusAndExpirationDateBetween(
                eq("default"), eq("ACTIVE"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0L);

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").value(10));
    }
}
