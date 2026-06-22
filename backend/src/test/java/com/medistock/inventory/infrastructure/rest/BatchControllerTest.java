package com.medistock.inventory.infrastructure.rest;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.inventory.application.service.*;
import com.medistock.inventory.domain.model.Batch;
import com.medistock.inventory.domain.model.InventoryDomainException;
import com.medistock.inventory.domain.model.Movement;
import com.medistock.inventory.domain.model.MovementType;
import com.medistock.inventory.domain.model.StockProjection;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.inventory.domain.port.MovementRepository;
import com.medistock.productcatalog.domain.port.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BatchController.class)
@Import(BatchControllerTest.TestSecurityConfig.class)
class BatchControllerTest {

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
    private ReceiveStockUseCase receiveStockUseCase;

    @MockitoBean
    private AdjustStockUseCase adjustStockUseCase;

    @MockitoBean
    private QuarantineUseCase quarantineUseCase;

    @MockitoBean
    private ReleaseFromQuarantineUseCase releaseFromQuarantineUseCase;

    @MockitoBean
    private WriteOffUseCase writeOffUseCase;

    @MockitoBean
    private GetStockSummaryUseCase getStockSummaryUseCase;

    @MockitoBean
    private BatchRepository batchRepository;

    @MockitoBean
    private MovementRepository movementRepository;

    @MockitoBean
    private ProductRepository productRepository;

    private UUID productId;
    private Batch sampleBatch;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("test-tenant");
        productId = UUID.randomUUID();
        sampleBatch = Batch.receive("test-tenant", productId, "BATCH-001",
                100, LocalDate.of(2027, Month.JUNE, 18), BigDecimal.valueOf(15.50));
    }

    @Test
    void shouldReceiveBatch() throws Exception {
        when(receiveStockUseCase.execute(any(), anyString(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(sampleBatch);

        CreateBatchRequest request = new CreateBatchRequest(
                productId, "BATCH-001", 100,
                LocalDate.of(2027, Month.JUNE, 18), null,
                BigDecimal.valueOf(15.50), null, null);

        mockMvc.perform(post("/api/v1/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.batchCode").value("BATCH-001"));
    }

    @Test
    void shouldListBatches() throws Exception {
        when(batchRepository.findAllByTenantId(anyString()))
                .thenReturn(List.of(sampleBatch));

        mockMvc.perform(get("/api/v1/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].batchCode").value("BATCH-001"));
    }

    @Test
    void shouldReturnEmptyListWhenNoBatches() throws Exception {
        when(batchRepository.findAllByTenantId(anyString()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldGetBatchById() throws Exception {
        when(batchRepository.findById(any()))
                .thenReturn(Optional.of(sampleBatch));
        when(movementRepository.findByBatchId(any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/batches/" + sampleBatch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.batchCode").value("BATCH-001"))
                .andExpect(jsonPath("$.movements").isArray());
    }

    @Test
    void shouldReturn409WhenBatchNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(batchRepository.findById(id))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/batches/" + id))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldQuarantineBatch() throws Exception {
        when(quarantineUseCase.execute(any(), anyString()))
                .thenReturn(sampleBatch);

        QuarantineRequest request = new QuarantineRequest("Quality issue");
        mockMvc.perform(post("/api/v1/batches/" + sampleBatch.getId() + "/quarantine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReleaseFromQuarantine() throws Exception {
        when(releaseFromQuarantineUseCase.execute(any(), anyString()))
                .thenReturn(sampleBatch);

        QuarantineRequest request = new QuarantineRequest("Cleared inspection");
        mockMvc.perform(post("/api/v1/batches/" + sampleBatch.getId() + "/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDisposeBatch() throws Exception {
        when(writeOffUseCase.execute(any(), anyString()))
                .thenReturn(sampleBatch);

        DisposeRequest request = new DisposeRequest("Contamination");
        mockMvc.perform(post("/api/v1/batches/" + sampleBatch.getId() + "/dispose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAdjustStock() throws Exception {
        when(adjustStockUseCase.execute(any(), anyInt(), anyString()))
                .thenReturn(sampleBatch);

        AdjustStockRequest request = new AdjustStockRequest(80, "Found damage");
        mockMvc.perform(post("/api/v1/batches/" + sampleBatch.getId() + "/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGetStockSummary() throws Exception {
        StockProjection projection = new StockProjection(
                productId, 100, 100, 0, 0, 0, 1,
                LocalDate.of(2027, Month.JUNE, 18));
        when(getStockSummaryUseCase.execute(productId))
                .thenReturn(projection);

        mockMvc.perform(get("/api/v1/batches/stock/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(100))
                .andExpect(jsonPath("$.availableQuantity").value(100));
    }
}
