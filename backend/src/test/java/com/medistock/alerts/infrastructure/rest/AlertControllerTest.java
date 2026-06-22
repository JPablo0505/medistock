package com.medistock.alerts.infrastructure.rest;

import com.medistock.alerts.application.service.*;
import com.medistock.alerts.domain.model.*;
import com.medistock.alerts.domain.port.AlertInstanceRepository;
import com.medistock.alerts.domain.port.AlertRuleRepository;
import com.medistock.alerts.infrastructure.rest.dto.AlertInstanceResponse;
import com.medistock.alerts.infrastructure.rest.dto.AlertRuleResponse;
import com.medistock.crosscutting.security.TenantContext;
import com.medistock.inventory.domain.model.Batch;
import com.medistock.inventory.domain.model.BatchStatus;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.port.ProductRepository;
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

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertController.class)
@Import(AlertControllerTest.TestSecurityConfig.class)
class AlertControllerTest {

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

    @MockitoBean private CreateAlertRuleUseCase createAlertRuleUseCase;
    @MockitoBean private UpdateAlertRuleUseCase updateAlertRuleUseCase;
    @MockitoBean private EvaluateAlertRulesUseCase evaluateAlertRulesUseCase;
    @MockitoBean private AcknowledgeAlertUseCase acknowledgeAlertUseCase;
    @MockitoBean private ResolveAlertUseCase resolveAlertUseCase;
    @MockitoBean private AlertRuleRepository alertRuleRepository;
    @MockitoBean private AlertInstanceRepository alertInstanceRepository;
    @MockitoBean private ProductRepository productRepository;
    @MockitoBean private BatchRepository batchRepository;

    private UUID ruleId;
    private AlertRule sampleRule;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("test-tenant");
        ruleId = UUID.randomUUID();
        sampleRule = new AlertRule("test-tenant", "Test Rule", "Desc",
                AlertRuleType.EXPIRATION, Map.of("expirationDays", 30));
        sampleProduct = Product.register("test-tenant", "Ibuprofeno", "Tablet",
                LocalDate.of(2026, Month.DECEMBER, 31),
                LocalDate.of(2025, Month.JANUARY, 1));
    }

    // ─── Alert Rules ──────────────────────────────────────────────

    @Test
    void shouldListRules() throws Exception {
        when(alertRuleRepository.findByType(anyString(), isNull()))
                .thenReturn(List.of(sampleRule));

        mockMvc.perform(get("/api/v1/alerts/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Rule"))
                .andExpect(jsonPath("$[0].type").value("EXPIRATION"));
    }

    @Test
    void shouldListRulesByType() throws Exception {
        when(alertRuleRepository.findByType(anyString(), eq("LOW_STOCK")))
                .thenReturn(List.of(sampleRule));

        mockMvc.perform(get("/api/v1/alerts/rules")
                        .param("type", "LOW_STOCK"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnEmptyListWhenNoRules() throws Exception {
        when(alertRuleRepository.findByType(anyString(), isNull()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/alerts/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldGetRuleById() throws Exception {
        when(alertRuleRepository.findById(ruleId))
                .thenReturn(Optional.of(sampleRule));

        mockMvc.perform(get("/api/v1/alerts/rules/{id}", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Rule"));
    }

    @Test
    void shouldReturn404WhenRuleNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(alertRuleRepository.findById(id))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/alerts/rules/{id}", id))
                .andExpect(status().isNotFound());
    }

    // ─── Alert Instances ──────────────────────────────────────────

    @Test
    void shouldListInstances() throws Exception {
        var instance = new AlertInstance("test-tenant", ruleId, sampleProduct.getId(), null,
                AlertSeverity.MEDIUM, "Test alert");
        when(alertInstanceRepository.findAll(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(instance));
        when(batchRepository.findByProductId(any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/alerts/instances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].severity").value("MEDIUM"))
                .andExpect(jsonPath("$[0].message").value("Test alert"));
    }

    @Test
    void shouldListInstancesByStatus() throws Exception {
        var instance = new AlertInstance("test-tenant", ruleId, sampleProduct.getId(), null,
                AlertSeverity.HIGH, "Active alert");
        when(alertInstanceRepository.findByStatus(anyString(), eq(AlertInstanceStatus.ACTIVE)))
                .thenReturn(List.of(instance));

        mockMvc.perform(get("/api/v1/alerts/instances")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void shouldReturnEmptyInstancesWhenNoAlerts() throws Exception {
        when(alertInstanceRepository.findAll(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/alerts/instances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldCountActiveAlerts() throws Exception {
        when(alertInstanceRepository.countByStatus(anyString(), eq(AlertInstanceStatus.ACTIVE)))
                .thenReturn(5L);

        mockMvc.perform(get("/api/v1/alerts/instances/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void shouldAcknowledgeAlert() throws Exception {
        UUID instanceId = UUID.randomUUID();
        var instance = new AlertInstance("test-tenant", ruleId, sampleProduct.getId(), null,
                AlertSeverity.MEDIUM, "Test");
        instance.acknowledge();
        when(acknowledgeAlertUseCase.execute(instanceId)).thenReturn(instance);

        mockMvc.perform(post("/api/v1/alerts/instances/{id}/acknowledge", instanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    void shouldResolveAlert() throws Exception {
        UUID instanceId = UUID.randomUUID();
        var instance = new AlertInstance("test-tenant", ruleId, sampleProduct.getId(), null,
                AlertSeverity.LOW, "Test");
        instance.resolve();
        when(resolveAlertUseCase.execute(instanceId)).thenReturn(instance);

        mockMvc.perform(post("/api/v1/alerts/instances/{id}/resolve", instanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void shouldEvaluateRules() throws Exception {
        when(evaluateAlertRulesUseCase.evaluateAll(anyString()))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/v1/alerts/evaluate"))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    @Test
    void shouldReturnCountOfTriggeredAlertsOnEvaluate() throws Exception {
        when(evaluateAlertRulesUseCase.evaluateAll(anyString()))
                .thenReturn(List.of(
                        new com.medistock.alerts.domain.event.AlertTriggeredEvent(
                                ruleId, sampleProduct.getId(), null, "Alert", "HIGH")));

        mockMvc.perform(post("/api/v1/alerts/evaluate"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }
}
