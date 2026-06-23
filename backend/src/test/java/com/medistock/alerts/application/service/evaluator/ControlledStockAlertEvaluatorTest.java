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
class ControlledStockAlertEvaluatorTest {

    private static final String TENANT = "tenant-1";
    private static final LocalDate EXPIRING_FAR = LocalDate.now().plusDays(100);
    private static final LocalDate MFR_DATE = LocalDate.now().minusDays(200);

    @Mock private AlertInstanceRepository alertInstanceRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private ProductRepository productRepository;

    private ControlledStockAlertEvaluator evaluator;
    private Product controlledProduct;
    private Product nonControlledProduct;
    private AlertRule controlledRule;

    @BeforeEach
    void setUp() {
        evaluator = new ControlledStockAlertEvaluator(
                alertInstanceRepository, batchRepository, productRepository);

        controlledProduct = Product.register(TENANT, "Controlado", "Tablet",
                EXPIRING_FAR, MFR_DATE);
        controlledProduct.update(controlledProduct.getName(), null, null, null, null, null,
                false, true, null, controlledProduct.getUnit(),
                BigDecimal.TEN, BigDecimal.ZERO, null,
                controlledProduct.getExpirationDate(), controlledProduct.getManufacturingDate());

        nonControlledProduct = Product.register(TENANT, "Normal", "Tablet",
                EXPIRING_FAR, MFR_DATE);

        controlledRule = new AlertRule(
                TENANT, "Controlado", "Desc",
                AlertRuleType.CONTROLLED_STOCK,
                Map.of("minStock", 5, "severity", "HIGH"));
    }

    // ─── SUPPORTED TYPE ────────────────────────────────────────────

    @Test
    void shouldReturnSupportedType() {
        assertThat(evaluator.supportedType()).isEqualTo(AlertRuleType.CONTROLLED_STOCK);
    }

    // ─── EVALUATION TESTS ──────────────────────────────────────────

    @Test
    void shouldCreateAlertWhenControlledProductBelowThreshold() {
        // Arrange
        var batch = Batch.receive(TENANT, controlledProduct.getId(), "BATCH-001", 3,
                EXPIRING_FAR, BigDecimal.TEN);

        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(controlledProduct));
        when(batchRepository.findByProductId(controlledProduct.getId())).thenReturn(List.of(batch));
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, controlledRule);

        // Assert
        assertThat(events).isNotEmpty();
    }

    @Test
    void shouldNotCreateAlertForNonControlledProduct() {
        // Arrange
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(nonControlledProduct));
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, controlledRule);

        // Assert
        assertThat(events).isEmpty();
    }

    @Test
    void shouldResolveStaleControlledStockAlert() {
        // Arrange
        var batch = Batch.receive(TENANT, controlledProduct.getId(), "BATCH-001", 10,
                EXPIRING_FAR, BigDecimal.TEN);

        var staleAlert = new AlertInstance(
                TENANT, controlledRule.getId(), controlledProduct.getId(), null,
                AlertSeverity.HIGH, "Old controlled stock alert");

        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(controlledProduct));
        when(batchRepository.findByProductId(controlledProduct.getId())).thenReturn(List.of(batch));
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE))
                .thenReturn(List.of(staleAlert));
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        evaluator.evaluate(TENANT, controlledRule);

        // Assert
        assertThat(staleAlert.getStatus()).isEqualTo(AlertInstanceStatus.RESOLVED);
    }

    @Test
    void shouldNotCreateAlertForControlledProductAboveThreshold() {
        // Arrange
        var batch = Batch.receive(TENANT, controlledProduct.getId(), "BATCH-001", 50,
                EXPIRING_FAR, BigDecimal.TEN);

        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(controlledProduct));
        when(batchRepository.findByProductId(controlledProduct.getId())).thenReturn(List.of(batch));
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, controlledRule);

        // Assert
        assertThat(events).isEmpty();
        verify(alertInstanceRepository, never()).save(any());
    }

    @Test
    void shouldNotCreateDuplicateControlledStockAlert() {
        // Arrange
        var batch = Batch.receive(TENANT, controlledProduct.getId(), "BATCH-001", 3,
                EXPIRING_FAR, BigDecimal.TEN);

        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(controlledProduct));
        when(batchRepository.findByProductId(controlledProduct.getId())).thenReturn(List.of(batch));
        when(alertInstanceRepository.existsActiveForRuleAndProduct(
                controlledRule.getId(), controlledProduct.getId())).thenReturn(true);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, controlledRule);

        // Assert
        assertThat(events).isEmpty();
        verify(alertInstanceRepository, never()).save(any());
    }
}
