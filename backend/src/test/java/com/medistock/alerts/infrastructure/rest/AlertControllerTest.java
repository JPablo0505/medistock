package com.medistock.alerts.infrastructure.rest;

import com.medistock.alerts.application.service.*;
import com.medistock.alerts.domain.model.*;
import com.medistock.alerts.domain.port.AlertInstanceRepository;
import com.medistock.alerts.domain.port.AlertRuleRepository;
import com.medistock.crosscutting.security.TenantContext;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.port.ProductRepository;
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
@DisplayName("AlertController REST endpoints")
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
    @DisplayName("GET /api/v1/alerts/rules — should list rules")
    void shouldListRules() throws Exception {
        // Arrange
        when(alertRuleRepository.findByType(anyString(), isNull()))
                .thenReturn(List.of(sampleRule));

        // Act & Assert
        mockMvc.perform(get("/api/v1/alerts/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Rule"))
                .andExpect(jsonPath("$[0].type").value("EXPIRATION"));
    }

    @Test
    @DisplayName("GET /api/v1/alerts/rules — should filter by type")
    void shouldListRulesByType() throws Exception {
        // Arrange
        when(alertRuleRepository.findByType(anyString(), eq("LOW_STOCK")))
                .thenReturn(List.of(sampleRule));

        // Act & Assert
        mockMvc.perform(get("/api/v1/alerts/rules")
                        .param("type", "LOW_STOCK"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/alerts/rules — should return empty list when no rules")
    void shouldReturnEmptyListWhenNoRules() throws Exception {
        // Arrange
        when(alertRuleRepository.findByType(anyString(), isNull()))
                .thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/v1/alerts/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/alerts/rules/{id} — should get rule by id")
    void shouldGetRuleById() throws Exception {
        // Arrange
        when(alertRuleRepository.findById(ruleId))
                .thenReturn(Optional.of(sampleRule));

        // Act & Assert
        mockMvc.perform(get("/api/v1/alerts/rules/{id}", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Rule"));
    }

    @Test
    @DisplayName("GET /api/v1/alerts/rules/{id} — should return 404 when rule not found")
    void shouldReturn404WhenRuleNotFound() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        when(alertRuleRepository.findById(id))
                .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/v1/alerts/rules/{id}", id))
                .andExpect(status().isNotFound());
    }

    // ─── Alert Instances ──────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/alerts/instances — should list instances")
    void shouldListInstances() throws Exception {
        // Arrange
        var instance = new AlertInstance("test-tenant", ruleId, sampleProduct.getId(), null,
                AlertSeverity.MEDIUM, "Test alert");
        when(alertInstanceRepository.findAll(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(instance));
        when(batchRepository.findByProductId(any()))
                .thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/v1/alerts/instances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].severity").value("MEDIUM"))
                .andExpect(jsonPath("$[0].message").value("Test alert"));
    }

    @Test
    @DisplayName("GET /api/v1/alerts/instances — should filter by status")
    void shouldListInstancesByStatus() throws Exception {
        // Arrange
        var instance = new AlertInstance("test-tenant", ruleId, sampleProduct.getId(), null,
                AlertSeverity.HIGH, "Active alert");
        when(alertInstanceRepository.findByStatus(anyString(), eq(AlertInstanceStatus.ACTIVE)))
                .thenReturn(List.of(instance));

        // Act & Assert
        mockMvc.perform(get("/api/v1/alerts/instances")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/v1/alerts/instances — should return empty when no alerts")
    void shouldReturnEmptyInstancesWhenNoAlerts() throws Exception {
        // Arrange
        when(alertInstanceRepository.findAll(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/v1/alerts/instances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/alerts/instances/count — should count active alerts")
    void shouldCountActiveAlerts() throws Exception {
        // Arrange
        when(alertInstanceRepository.countByStatus(anyString(), eq(AlertInstanceStatus.ACTIVE)))
                .thenReturn(5L);

        // Act & Assert
        mockMvc.perform(get("/api/v1/alerts/instances/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    @DisplayName("POST /api/v1/alerts/instances/{id}/acknowledge — should acknowledge alert")
    void shouldAcknowledgeAlert() throws Exception {
        // Arrange
        UUID instanceId = UUID.randomUUID();
        var instance = new AlertInstance("test-tenant", ruleId, sampleProduct.getId(), null,
                AlertSeverity.MEDIUM, "Test");
        instance.acknowledge();
        when(acknowledgeAlertUseCase.execute(instanceId)).thenReturn(instance);

        // Act & Assert
        mockMvc.perform(post("/api/v1/alerts/instances/{id}/acknowledge", instanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    @DisplayName("POST /api/v1/alerts/instances/{id}/resolve — should resolve alert")
    void shouldResolveAlert() throws Exception {
        // Arrange
        UUID instanceId = UUID.randomUUID();
        var instance = new AlertInstance("test-tenant", ruleId, sampleProduct.getId(), null,
                AlertSeverity.LOW, "Test");
        instance.resolve();
        when(resolveAlertUseCase.execute(instanceId)).thenReturn(instance);

        // Act & Assert
        mockMvc.perform(post("/api/v1/alerts/instances/{id}/resolve", instanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    @DisplayName("POST /api/v1/alerts/evaluate — should evaluate rules")
    void shouldEvaluateRules() throws Exception {
        // Arrange
        when(evaluateAlertRulesUseCase.evaluateAll(anyString()))
                .thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(post("/api/v1/alerts/evaluate"))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    @Test
    @DisplayName("POST /api/v1/alerts/evaluate — should return count of triggered alerts")
    void shouldReturnCountOfTriggeredAlertsOnEvaluate() throws Exception {
        // Arrange
        when(evaluateAlertRulesUseCase.evaluateAll(anyString()))
                .thenReturn(List.of(
                        new com.medistock.alerts.domain.event.AlertTriggeredEvent(
                                ruleId, sampleProduct.getId(), null, "Alert", "HIGH")));

        // Act & Assert
        mockMvc.perform(post("/api/v1/alerts/evaluate"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    // ─── Create / Update / Toggle / Delete ──────────────────────────

    @Test
    @DisplayName("POST /api/v1/alerts/rules — should create alert rule")
    void shouldCreateRule() throws Exception {
        // Arrange
        when(createAlertRuleUseCase.execute(anyString(), anyString(), anyString(),
                anyString(), anyMap())).thenReturn(sampleRule);

        // Act & Assert
        mockMvc.perform(post("/api/v1/alerts/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", "test-tenant")
                        .content("""
                                {
                                    "name": "Test Rule",
                                    "description": "Desc",
                                    "type": "EXPIRATION",
                                    "config": {"expirationDays": 30}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Test Rule"));
    }

    @Test
    @DisplayName("POST /api/v1/alerts/rules — should use default tenant when header missing")
    void shouldCreateRuleWithDefaultTenant() throws Exception {
        // Arrange
        TenantContext.clear();
        when(createAlertRuleUseCase.execute(eq("default"), anyString(), anyString(),
                anyString(), anyMap())).thenReturn(sampleRule);

        // Act & Assert
        mockMvc.perform(post("/api/v1/alerts/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Rule",
                                    "description": "D",
                                    "type": "EXPIRATION",
                                    "config": {}
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /api/v1/alerts/rules/{id} — should update alert rule")
    void shouldUpdateRule() throws Exception {
        // Arrange
        when(updateAlertRuleUseCase.execute(any(), anyString(), anyString(),
                anyString(), anyMap())).thenReturn(sampleRule);

        // Act & Assert
        mockMvc.perform(put("/api/v1/alerts/rules/{id}", ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Updated Rule",
                                    "description": "Updated",
                                    "type": "LOW_STOCK",
                                    "config": {"threshold": 10}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Rule"));
    }

    @Test
    @DisplayName("POST /api/v1/alerts/rules/{id}/toggle — should toggle rule active state")
    void shouldToggleRule() throws Exception {
        // Arrange
        when(alertRuleRepository.findById(ruleId))
                .thenReturn(Optional.of(sampleRule));

        // Act & Assert
        mockMvc.perform(post("/api/v1/alerts/rules/{id}/toggle", ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Rule"));
    }

    @Test
    @DisplayName("POST /api/v1/alerts/rules/{id}/toggle — should return 404 when rule not found")
    void shouldToggleRuleNotFound() throws Exception {
        // Arrange
        when(alertRuleRepository.findById(ruleId))
                .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(post("/api/v1/alerts/rules/{id}/toggle", ruleId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/v1/alerts/rules/{id} — should delete rule")
    void shouldDeleteRule() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/v1/alerts/rules/{id}", ruleId))
                .andExpect(status().isNoContent());
    }
}
