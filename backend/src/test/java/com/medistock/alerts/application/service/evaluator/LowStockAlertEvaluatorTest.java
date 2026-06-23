package com.medistock.alerts.application.service.evaluator;

import com.medistock.alerts.domain.event.AlertTriggeredEvent;
import com.medistock.alerts.domain.model.*;
import com.medistock.alerts.domain.port.AlertInstanceRepository;
import com.medistock.inventory.domain.model.Batch;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LowStockAlertEvaluatorTest {

    private static final String TENANT = "tenant-1";
    private static final LocalDate EXPIRING_FAR = LocalDate.now().plusDays(100);
    private static final LocalDate MFR_DATE = LocalDate.now().minusDays(200);

    @Mock private AlertInstanceRepository alertInstanceRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private ProductRepository productRepository;

    private LowStockAlertEvaluator evaluator;
    private Product product;
    private AlertRule lowStockRule;

    @BeforeEach
    void setUp() {
        evaluator = new LowStockAlertEvaluator(
                alertInstanceRepository, batchRepository, productRepository);

        product = Product.register(TENANT, "Ibuprofeno 600mg", "Tablet",
                EXPIRING_FAR, MFR_DATE);

        lowStockRule = new AlertRule(
                TENANT, "Stock bajo", "Desc",
                AlertRuleType.LOW_STOCK,
                Map.of("minStock", 10, "severity", "LOW"));
    }

    // ─── SUPPORTED TYPE ────────────────────────────────────────────

    @Test
    void shouldReturnSupportedType() {
        assertThat(evaluator.supportedType()).isEqualTo(AlertRuleType.LOW_STOCK);
    }

    // ─── EVALUATION TESTS ──────────────────────────────────────────

    @Test
    void shouldCreateAlertWhenStockBelowThreshold() {
        // Arrange
        var batch = Batch.receive(TENANT, product.getId(), "BATCH-001", 5,
                EXPIRING_FAR, BigDecimal.TEN);

        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(product));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of(batch));
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, lowStockRule);

        // Assert
        assertThat(events).isNotEmpty();
        verify(alertInstanceRepository).save(any());
    }

    @Test
    void shouldCreateHighSeverityAlertWhenStockIsZero() {
        // Arrange
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(product));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, lowStockRule);

        // Assert
        assertThat(events).isNotEmpty();
        assertThat(events.getFirst().severity()).isEqualTo("HIGH");
    }

    @Test
    void shouldNotCreateAlertWhenStockAboveThreshold() {
        // Arrange
        var batch = Batch.receive(TENANT, product.getId(), "BATCH-001", 100,
                EXPIRING_FAR, BigDecimal.TEN);

        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(product));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of(batch));
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, lowStockRule);

        // Assert
        assertThat(events).isEmpty();
        verify(alertInstanceRepository, never()).save(any());
    }

    @Test
    void shouldResolveStaleLowStockAlert() {
        // Arrange
        var batch = Batch.receive(TENANT, product.getId(), "BATCH-001", 100,
                EXPIRING_FAR, BigDecimal.TEN);

        var staleAlert = new AlertInstance(
                TENANT, lowStockRule.getId(), product.getId(), null,
                AlertSeverity.LOW, "Old low stock alert");

        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(product));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of(batch));
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE))
                .thenReturn(List.of(staleAlert));
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        evaluator.evaluate(TENANT, lowStockRule);

        // Assert
        assertThat(staleAlert.getStatus()).isEqualTo(AlertInstanceStatus.RESOLVED);
    }

    @Test
    void shouldOnlyCountActiveBatchesForLowStock() {
        // Arrange
        var activeBatch = Batch.receive(TENANT, product.getId(), "BATCH-ACTIVE", 5,
                EXPIRING_FAR, BigDecimal.TEN);
        var quarantinedBatch = Batch.receive(TENANT, product.getId(), "BATCH-Q", 50,
                EXPIRING_FAR, BigDecimal.TEN);
        quarantinedBatch.quarantine();

        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(product));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of(activeBatch, quarantinedBatch));
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, lowStockRule);

        // Assert
        assertThat(events).isNotEmpty();
    }

    @Test
    void shouldNotCreateDuplicateLowStockAlert() {
        // Arrange
        var batch = Batch.receive(TENANT, product.getId(), "BATCH-001", 3,
                EXPIRING_FAR, BigDecimal.TEN);

        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(product));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of(batch));
        when(alertInstanceRepository.existsActiveForRuleAndProduct(
                lowStockRule.getId(), product.getId())).thenReturn(true);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, lowStockRule);

        // Assert
        assertThat(events).isEmpty();
        verify(alertInstanceRepository, never()).save(any());
    }
}
