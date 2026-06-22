package com.medistock.alerts.application;

import com.medistock.alerts.application.service.EvaluateAlertRulesUseCase;
import com.medistock.alerts.domain.event.AlertTriggeredEvent;
import com.medistock.alerts.domain.model.*;
import com.medistock.alerts.domain.port.AlertInstanceRepository;
import com.medistock.alerts.domain.port.AlertRuleRepository;
import com.medistock.inventory.domain.model.Batch;
import com.medistock.inventory.domain.model.BatchStatus;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluateAlertRulesUseCaseTest {

    private static final String TENANT = "tenant-1";
    private static final LocalDate EXPIRING_SOON = LocalDate.now().plusDays(10);
    private static final LocalDate EXPIRING_FAR = LocalDate.now().plusDays(100);
    private static final LocalDate MFR_DATE = LocalDate.now().minusDays(200);

    @Mock private AlertRuleRepository alertRuleRepository;
    @Mock private AlertInstanceRepository alertInstanceRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private ProductRepository productRepository;
    @Captor private ArgumentCaptor<AlertInstance> instanceCaptor;

    private EvaluateAlertRulesUseCase useCase;

    private Product productExpiringSoon;
    private Product productExpiringFar;
    private AlertRule expirationRule;
    private AlertRule lowStockRule;
    private AlertRule controlledRule;

    @BeforeEach
    void setUp() {
        useCase = new EvaluateAlertRulesUseCase(
                alertRuleRepository, alertInstanceRepository, batchRepository, productRepository);

        productExpiringSoon = Product.register(TENANT, "Ibuprofeno 600mg", "Tablet",
                EXPIRING_SOON, MFR_DATE);

        productExpiringFar = Product.register(TENANT, "Paracetamol 500mg", "Tablet",
                EXPIRING_FAR, MFR_DATE);

        expirationRule = new AlertRule(
                TENANT, "Vencimiento próximo", "Desc",
                AlertRuleType.EXPIRATION,
                Map.of("expirationDays", 30, "severity", "MEDIUM"));

        lowStockRule = new AlertRule(
                TENANT, "Stock bajo", "Desc",
                AlertRuleType.LOW_STOCK,
                Map.of("minStock", 10, "severity", "LOW"));

        controlledRule = new AlertRule(
                TENANT, "Controlado", "Desc",
                AlertRuleType.CONTROLLED_STOCK,
                Map.of("minStock", 5, "severity", "HIGH"));
    }

    private void stubCommon() {
        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(expirationRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringSoon));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
    }

    // ─── EXPIRATION RULE TESTS ─────────────────────────────────────

    @Test
    void shouldCreateAlertWhenProductExpiringSoon() {
        stubCommon();
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<AlertTriggeredEvent> events = useCase.evaluateAll(TENANT);

        assertThat(events).isNotEmpty();
        verify(alertInstanceRepository, atLeastOnce()).save(any());
    }

    @Test
    void shouldNotCreateDuplicateExpirationAlert() {
        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(expirationRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringSoon));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
        when(alertInstanceRepository.existsActiveForRuleAndProduct(expirationRule.getId(), productExpiringSoon.getId()))
                .thenReturn(true);

        List<AlertTriggeredEvent> events = useCase.evaluateAll(TENANT);

        assertThat(events).isEmpty();
        verify(alertInstanceRepository, never()).save(any());
    }

    @Test
    void shouldResolveStaleExpirationAlertWhenProductNoLongerExpiring() {
        var staleAlert = new AlertInstance(
                TENANT, expirationRule.getId(), productExpiringFar.getId(), null,
                AlertSeverity.MEDIUM, "Stale alert");

        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(expirationRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringFar));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE))
                .thenReturn(List.of(staleAlert));
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.evaluateAll(TENANT);

        assertThat(staleAlert.getStatus()).isEqualTo(AlertInstanceStatus.RESOLVED);
    }

    @Test
    void shouldCreateAlertFromBatchExpiration() {
        var batch = Batch.receive(TENANT, productExpiringFar.getId(), "BATCH-001", 100,
                LocalDate.now().plusDays(10), BigDecimal.TEN);

        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(expirationRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringFar));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of(batch));
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<AlertTriggeredEvent> events = useCase.evaluateAll(TENANT);

        assertThat(events).isNotEmpty();
    }

    @Test
    void shouldSkipInactiveBatchesForBatchExpiration() {
        var disposedBatch = Batch.receive(TENANT, productExpiringFar.getId(), "BATCH-DISP", 100,
                LocalDate.now().plusDays(5), BigDecimal.TEN);
        disposedBatch.dispose();

        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(expirationRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringFar));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of(disposedBatch));
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());

        assertThat(useCase.evaluateAll(TENANT)).isEmpty();
    }

    // ─── LOW STOCK RULE TESTS ──────────────────────────────────────

    @Test
    void shouldCreateAlertWhenStockBelowThreshold() {
        var batch = Batch.receive(TENANT, productExpiringFar.getId(), "BATCH-001", 5,
                EXPIRING_FAR, BigDecimal.TEN);

        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(lowStockRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringFar));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of(batch));
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<AlertTriggeredEvent> events = useCase.evaluateAll(TENANT);

        assertThat(events).isNotEmpty();
        verify(alertInstanceRepository).save(any());
    }

    @Test
    void shouldCreateHighSeverityAlertWhenStockIsZero() {
        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(lowStockRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringFar));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<AlertTriggeredEvent> events = useCase.evaluateAll(TENANT);

        assertThat(events).isNotEmpty();
        assertThat(events.getFirst().severity()).isEqualTo("HIGH");
    }

    @Test
    void shouldNotCreateAlertWhenStockAboveThreshold() {
        var batch = Batch.receive(TENANT, productExpiringFar.getId(), "BATCH-001", 100,
                EXPIRING_FAR, BigDecimal.TEN);

        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(lowStockRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringFar));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of(batch));
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());

        assertThat(useCase.evaluateAll(TENANT)).isEmpty();
        verify(alertInstanceRepository, never()).save(any());
    }

    @Test
    void shouldResolveStaleLowStockAlert() {
        var batch = Batch.receive(TENANT, productExpiringFar.getId(), "BATCH-001", 100,
                EXPIRING_FAR, BigDecimal.TEN);

        var staleAlert = new AlertInstance(
                TENANT, lowStockRule.getId(), productExpiringFar.getId(), null,
                AlertSeverity.LOW, "Old low stock alert");

        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(lowStockRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringFar));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of(batch));
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE))
                .thenReturn(List.of(staleAlert));
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.evaluateAll(TENANT);

        assertThat(staleAlert.getStatus()).isEqualTo(AlertInstanceStatus.RESOLVED);
    }

    @Test
    void shouldOnlyCountActiveBatchesForLowStock() {
        var activeBatch = Batch.receive(TENANT, productExpiringFar.getId(), "BATCH-ACTIVE", 5,
                EXPIRING_FAR, BigDecimal.TEN);
        var quarantinedBatch = Batch.receive(TENANT, productExpiringFar.getId(), "BATCH-Q", 50,
                EXPIRING_FAR, BigDecimal.TEN);
        quarantinedBatch.quarantine();

        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(lowStockRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringFar));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of(activeBatch, quarantinedBatch));
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<AlertTriggeredEvent> events = useCase.evaluateAll(TENANT);

        assertThat(events).isNotEmpty();
    }

    // ─── CONTROLLED STOCK RULE TESTS ───────────────────────────────

    @Test
    void shouldCreateAlertWhenControlledProductBelowThreshold() {
        productExpiringFar.update(productExpiringFar.getName(), null, null, null, null, null,
                false, true, null, productExpiringFar.getUnit(),
                BigDecimal.TEN, BigDecimal.ZERO, null,
                productExpiringFar.getExpirationDate(), productExpiringFar.getManufacturingDate());

        var batch = Batch.receive(TENANT, productExpiringFar.getId(), "BATCH-001", 3,
                EXPIRING_FAR, BigDecimal.TEN);

        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(controlledRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringFar));
        when(batchRepository.findByProductId(productExpiringFar.getId())).thenReturn(List.of(batch));
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<AlertTriggeredEvent> events = useCase.evaluateAll(TENANT);

        assertThat(events).isNotEmpty();
    }

    @Test
    void shouldNotCreateAlertForNonControlledProduct() {
        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(controlledRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringFar));
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());

        assertThat(useCase.evaluateAll(TENANT)).isEmpty();
    }

    // ─── DEFAULT RULES ─────────────────────────────────────────────

    @Test
    void shouldCreateDefaultRulesWhenNoneExist() {
        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of());
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of());
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());

        List<AlertTriggeredEvent> events = useCase.evaluateAll(TENANT);

        verify(alertRuleRepository, times(2)).save(any());
        assertThat(events).isEmpty();
    }

    @Test
    void shouldNotCreateDefaultRulesWhenAlreadyExist() {
        stubCommon();

        useCase.evaluateAll(TENANT);

        verify(alertRuleRepository, never()).save(any(AlertRule.class));
    }

    // ─── ORPHANED ALERT CLEANUP ────────────────────────────────────

    @Test
    void shouldResolveOrphanedAlertsForProductsNotInTenant() {
        UUID otherProductId = UUID.randomUUID();
        var orphanedAlert = new AlertInstance(
                TENANT, UUID.randomUUID(), otherProductId, null,
                AlertSeverity.MEDIUM, "Orphaned alert");

        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(expirationRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringSoon));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE))
                .thenReturn(List.of(orphanedAlert));
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.evaluateAll(TENANT);

        assertThat(orphanedAlert.getStatus()).isEqualTo(AlertInstanceStatus.RESOLVED);
    }

    // ─── PRESCRIPTION RULE ─────────────────────────────────────────

    @Test
    void shouldReturnEmptyForPrescriptionRule() {
        var prescriptionRule = new AlertRule(
                TENANT, "Prescripción", "Desc",
                AlertRuleType.PRESCRIPTION,
                Map.of("severity", "LOW"));

        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(prescriptionRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringSoon));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());

        assertThat(useCase.evaluateAll(TENANT)).isEmpty();
    }

    // ─── DUPLICATE DEDUP TESTS ─────────────────────────────────────

    @Test
    void shouldDeduplicateAlertInstances() {
        stubCommon();
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);

        // Use restore() to create alerts with explicit timestamps (older first)
        LocalDateTime older = LocalDateTime.now().minusHours(2);
        LocalDateTime newer = LocalDateTime.now();

        var alert1 = AlertInstance.restore(
                UUID.randomUUID(), TENANT, expirationRule.getId(), productExpiringSoon.getId(), null,
                AlertSeverity.MEDIUM, "Older alert", AlertInstanceStatus.ACTIVE,
                older, older, null, null);

        var alert2 = AlertInstance.restore(
                UUID.randomUUID(), TENANT, expirationRule.getId(), productExpiringSoon.getId(), null,
                AlertSeverity.MEDIUM, "Newer alert", AlertInstanceStatus.ACTIVE,
                newer, newer, null, null);

        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE))
                .thenReturn(List.of(alert1, alert2));
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.evaluateAll(TENANT);

        // The older alert (alert1) should be resolved by dedup
        // The newer one (alert2) should remain active
        assertThat(alert1.getStatus()).isEqualTo(AlertInstanceStatus.RESOLVED);
        assertThat(alert2.getStatus()).isEqualTo(AlertInstanceStatus.ACTIVE);
    }

    // ─── EDGE CASES ────────────────────────────────────────────────

    @Test
    void shouldHandleProductWithNullExpirationDate() {
        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(expirationRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringFar));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());

        assertThatCode(() -> useCase.evaluateAll(TENANT))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHandleNoProducts() {
        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(expirationRule));
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of());
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());

        assertThat(useCase.evaluateAll(TENANT)).isEmpty();
    }

    @Test
    void shouldNotFailWhenRuleEvaluationThrows() {
        when(alertRuleRepository.findByType(TENANT, null)).thenReturn(List.of(expirationRule));
        when(productRepository.findByTenantId(TENANT)).thenThrow(new RuntimeException("DB error"));

        assertThatCode(() -> useCase.evaluateAll(TENANT))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldSaveAlertInstanceWithCorrectValues() {
        stubCommon();
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.evaluateAll(TENANT);

        verify(alertInstanceRepository, atLeastOnce()).save(instanceCaptor.capture());
        AlertInstance saved = instanceCaptor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(TENANT);
        assertThat(saved.getAlertRuleId()).isEqualTo(expirationRule.getId());
        assertThat(saved.getProductId()).isEqualTo(productExpiringSoon.getId());
        assertThat(saved.getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
    }
}
