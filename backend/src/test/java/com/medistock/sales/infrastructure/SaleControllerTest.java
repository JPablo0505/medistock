package com.medistock.sales.infrastructure;

import com.medistock.sales.application.service.CreateSaleUseCase;
import com.medistock.sales.application.service.VoidSaleUseCase;
import com.medistock.sales.domain.model.*;
import com.medistock.sales.domain.port.SaleRepository;
import com.medistock.sales.infrastructure.rest.SaleController;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SaleController.class)
@Import(SaleControllerTest.TestSecurityConfig.class)
class SaleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateSaleUseCase createSaleUseCase;

    @MockitoBean
    private VoidSaleUseCase voidSaleUseCase;

    @MockitoBean
    private SaleRepository saleRepository;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();

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

    @Test
    void shouldCreateSale() throws Exception {
        BatchAllocation alloc = new BatchAllocation(BATCH_ID, "LOT-001",
                LocalDate.of(2027, 6, 1), 10, BigDecimal.valueOf(5.00));
        SaleItem item = new SaleItem(PRODUCT_ID, "Ibuprofeno", "IBU-400",
                10, BigDecimal.valueOf(15.50), List.of(alloc));
        Sale sale = Sale.create("tenant-1", "user-1", List.of(item));

        when(createSaleUseCase.execute(any())).thenReturn(sale);

        mockMvc.perform(post("/api/v1/sales")
                        .header("X-Tenant-Id", "tenant-1")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [{"productId": "%s", "productName": "Ibuprofeno", "quantity": 10, "unitPrice": 15.50}]
                                }
                                """.formatted(PRODUCT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.total").value(155.00));
    }

    @Test
    void shouldListSales() throws Exception {
        when(saleRepository.findByTenantWithFilters(any(), any(), any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/sales")
                        .header("X-Tenant-Id", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldGetSaleById() throws Exception {
        UUID saleId = UUID.randomUUID();
        BatchAllocation alloc = new BatchAllocation(BATCH_ID, "LOT-001",
                LocalDate.of(2027, 6, 1), 10, BigDecimal.valueOf(5.00));
        SaleItem item = new SaleItem(PRODUCT_ID, "Ibuprofeno", "IBU-400",
                10, BigDecimal.valueOf(15.50), List.of(alloc));
        Sale sale = Sale.create("tenant-1", "user-1", List.of(item));

        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));

        mockMvc.perform(get("/api/v1/sales/{id}", saleId)
                        .header("X-Tenant-Id", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void shouldReturn404WhenSaleNotFound() throws Exception {
        UUID saleId = UUID.randomUUID();
        when(saleRepository.findById(saleId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/sales/{id}", saleId)
                        .header("X-Tenant-Id", "tenant-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldVoidSale() throws Exception {
        UUID saleId = UUID.randomUUID();
        BatchAllocation alloc = new BatchAllocation(BATCH_ID, "LOT-001",
                LocalDate.of(2027, 6, 1), 10, BigDecimal.valueOf(5.00));
        SaleItem item = new SaleItem(PRODUCT_ID, "Ibuprofeno", "IBU-400",
                10, BigDecimal.valueOf(15.50), List.of(alloc));
        Sale sale = Sale.create("tenant-1", "user-1", List.of(item));
        sale.voidSale("Customer return");

        when(voidSaleUseCase.execute(saleId, "Customer return")).thenReturn(sale);

        mockMvc.perform(post("/api/v1/sales/{id}/void", saleId)
                        .header("X-Tenant-Id", "tenant-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Customer return"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"));
    }
}
