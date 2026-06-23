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
class ExpirationAlertEvaluatorTest {

    private static final String TENANT = "tenant-1";
    private static final LocalDate EXPIRING_SOON = LocalDate.now().plusDays(10);
    private static final LocalDate EXPIRING_FAR = LocalDate.now().plusDays(100);
    private static final LocalDate EXPIRING_PAST = LocalDate.now().minusDays(5);
    private static final LocalDate MFR_DATE = LocalDate.now().minusDays(200);

    @Mock private AlertInstanceRepository alertInstanceRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private ProductRepository productRepository;

    private ExpirationAlertEvaluator evaluator;
    private Product productExpiringSoon;
    private Product productExpiringFar;
    private Product productExpired;
    private AlertRule expirationRule;

    @BeforeEach
    void setUp() {
        evaluator = new ExpirationAlertEvaluator(
                alertInstanceRepository, batchRepository, productRepository);

        productExpiringSoon = Product.register(TENANT, "Ibuprofeno 600mg", "Tablet",
                EXPIRING_SOON, MFR_DATE);

        productExpiringFar = Product.register(TENANT, "Paracetamol 500mg", "Tablet",
                EXPIRING_FAR, MFR_DATE);

        productExpired = Product.register(TENANT, "Vencido", "Tablet",
                EXPIRING_PAST, MFR_DATE);

        expirationRule = new AlertRule(
                TENANT, "Vencimiento próximo", "Desc",
                AlertRuleType.EXPIRATION,
                Map.of("expirationDays", 30, "severity", "MEDIUM"));
    }

    // ─── SUPPORTED TYPE ────────────────────────────────────────────

    @Test
    void shouldReturnSupportedType() {
        assertThat(evaluator.supportedType()).isEqualTo(AlertRuleType.EXPIRATION);
    }

    // ─── EVALUATION TESTS ──────────────────────────────────────────

    @Test
    void shouldCreateAlertWhenProductExpiringSoon() {
        // Arrange
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringSoon));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, expirationRule);

        // Assert
        assertThat(events).isNotEmpty();
        verify(alertInstanceRepository, atLeastOnce()).save(any());
    }

    @Test
    void shouldNotCreateDuplicateExpirationAlert() {
        // Arrange
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringSoon));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.existsActiveForRuleAndProduct(
                expirationRule.getId(), productExpiringSoon.getId())).thenReturn(true);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, expirationRule);

        // Assert
        assertThat(events).isEmpty();
        verify(alertInstanceRepository, never()).save(any());
    }

    @Test
    void shouldResolveStaleExpirationAlertWhenProductNoLongerExpiring() {
        // Arrange
        var staleAlert = new AlertInstance(
                TENANT, expirationRule.getId(), productExpiringFar.getId(), null,
                AlertSeverity.MEDIUM, "Stale alert");

        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringFar));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE))
                .thenReturn(List.of(staleAlert));
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        evaluator.evaluate(TENANT, expirationRule);

        // Assert
        assertThat(staleAlert.getStatus()).isEqualTo(AlertInstanceStatus.RESOLVED);
    }

    @Test
    void shouldCreateAlertFromBatchExpiration() {
        // Arrange
        var batch = Batch.receive(TENANT, productExpiringFar.getId(), "BATCH-001", 100,
                LocalDate.now().plusDays(10), BigDecimal.TEN);

        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringFar));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of(batch));
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, expirationRule);

        // Assert
        assertThat(events).isNotEmpty();
    }

    @Test
    void shouldSkipInactiveBatchesForBatchExpiration() {
        // Arrange
        var disposedBatch = Batch.receive(TENANT, productExpiringFar.getId(), "BATCH-DISP", 100,
                LocalDate.now().plusDays(5), BigDecimal.TEN);
        disposedBatch.dispose();

        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringFar));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of(disposedBatch));
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, expirationRule);

        // Assert
        assertThat(events).isEmpty();
    }

    @Test
    void shouldCreateAlertForAlreadyExpiredProduct() {
        // Arrange
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpired));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, expirationRule);

        // Assert
        assertThat(events).isNotEmpty();
        assertThat(events.getFirst().message()).contains("venció");
    }

    @Test
    void shouldNotFailWhenProductExpirationDateIsFarInFuture() {
        // Arrange
        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(productExpiringFar));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());

        // Act & Assert
        assertThatCode(() -> evaluator.evaluate(TENANT, expirationRule))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldNotCreateAlertWhenProductExpiresIn31Days() {
        // Arrange: rule has expirationDays = 30, product expires in 31 days
        var product31 = Product.register(TENANT, "Safe Product", "Tablet",
                LocalDate.now().plusDays(31), MFR_DATE);

        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(product31));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, expirationRule);

        // Assert
        assertThat(events).isEmpty();
        verify(alertInstanceRepository, never()).save(any());
    }

    @Test
    void shouldCreateAlertWhenProductExpiresInExactly30Days() {
        // Arrange: rule has expirationDays = 30, product expires in exactly 30 days
        var product30 = Product.register(TENANT, "Borderline Product", "Tablet",
                LocalDate.now().plusDays(30), MFR_DATE);

        when(productRepository.findByTenantId(TENANT)).thenReturn(List.of(product30));
        when(batchRepository.findAllByTenantId(TENANT)).thenReturn(List.of());
        when(alertInstanceRepository.existsActiveForRuleAndProduct(any(), any())).thenReturn(false);
        when(alertInstanceRepository.findByStatus(TENANT, AlertInstanceStatus.ACTIVE)).thenReturn(List.of());
        when(alertInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        List<AlertTriggeredEvent> events = evaluator.evaluate(TENANT, expirationRule);

        // Assert
        assertThat(events).isNotEmpty();
    }
}
