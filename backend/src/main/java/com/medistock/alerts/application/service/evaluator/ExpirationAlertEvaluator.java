package com.medistock.alerts.application.service.evaluator;

import com.medistock.alerts.domain.event.AlertTriggeredEvent;
import com.medistock.alerts.domain.model.*;
import com.medistock.alerts.domain.port.AlertInstanceRepository;
import com.medistock.alerts.domain.service.AlertRuleEvaluator;
import com.medistock.inventory.domain.model.Batch;
import com.medistock.inventory.domain.model.BatchStatus;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ExpirationAlertEvaluator implements AlertRuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ExpirationAlertEvaluator.class);

    private final AlertInstanceRepository alertInstanceRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;

    public ExpirationAlertEvaluator(AlertInstanceRepository alertInstanceRepository,
                                    BatchRepository batchRepository,
                                    ProductRepository productRepository) {
        this.alertInstanceRepository = alertInstanceRepository;
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
    }

    @Override
    public AlertRuleType supportedType() {
        return AlertRuleType.EXPIRATION;
    }

    @Override
    public List<AlertTriggeredEvent> evaluate(String tenantId, AlertRule rule) {
        List<AlertTriggeredEvent> events = new ArrayList<>();
        int days = rule.getExpirationDays();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        // ── Check products for expiration ────────────────────────────────
        List<Product> products = productRepository.findByTenantId(tenantId);
        log.debug("Expiration check: found {} products for tenant={}", products.size(), tenantId);
        Set<UUID> triggeringProductIds = new HashSet<>();

        for (Product product : products) {
            checkProductExpiration(rule, product, days, today, triggeringProductIds, events);
        }

        // ── Also check batch-level expiration ─────────────────────────────
        List<Batch> batches = batchRepository.findAllByTenantId(tenantId);
        for (Batch batch : batches) {
            checkBatchExpiration(rule, batch, days, today, triggeringProductIds, events);
        }

        // ── Close stale alerts for products no longer triggering ─────────
        closeStaleExpirationAlerts(rule, products, today, days);

        return events;
    }

    private void checkProductExpiration(AlertRule rule, Product product, int days, LocalDate today,
                                        Set<UUID> triggeringProductIds, List<AlertTriggeredEvent> events) {
        LocalDate expDate = product.getExpirationDate();
        if (expDate == null) return;

        long daysUntilExpiry = ChronoUnit.DAYS.between(today, expDate);

        if (daysUntilExpiry > days) return;

        triggeringProductIds.add(product.getId());

        if (alertInstanceRepository.existsActiveForRuleAndProduct(rule.getId(), product.getId())) return;

        String msg = daysUntilExpiry < 0
                ? String.format("Medicamento %s venció hace %d días (%s)",
                product.getName(), Math.abs(daysUntilExpiry), expDate)
                : String.format("Medicamento %s vence en %d días (%s)",
                product.getName(), daysUntilExpiry, expDate);

        AlertTriggeredEvent event = new AlertTriggeredEvent(
                rule.getId(), product.getId(), null,
                msg, rule.getSeverity().name());
        saveInstance(event, rule);
        events.add(event);
        log.info("Expiration alert: product={}, expires={}, daysUntil={}",
                product.getName(), expDate, daysUntilExpiry);
    }

    private void checkBatchExpiration(AlertRule rule, Batch batch, int days, LocalDate today,
                                      Set<UUID> triggeringProductIds, List<AlertTriggeredEvent> events) {
        if (batch.getStatus() != BatchStatus.ACTIVE) return;

        LocalDate expDate = batch.getExpirationDate();
        if (expDate == null) return;

        long daysUntilBatchExpiry = ChronoUnit.DAYS.between(today, expDate);
        if (daysUntilBatchExpiry > days) return;
        if (triggeringProductIds.contains(batch.getProductId())) return;

        if (alertInstanceRepository.existsActiveForRuleAndProduct(rule.getId(), batch.getProductId())) return;

        String msg = daysUntilBatchExpiry >= 0
                ? String.format("Lote %s del producto vence en %d días (%s)",
                batch.getBatchCode(), daysUntilBatchExpiry, expDate)
                : String.format("Lote %s del producto venció hace %d días (%s)",
                batch.getBatchCode(), Math.abs(daysUntilBatchExpiry), expDate);
        AlertTriggeredEvent event = new AlertTriggeredEvent(
                rule.getId(), batch.getProductId(), batch.getId(),
                msg, rule.getSeverity().name());
        saveInstance(event, rule);
        events.add(event);
        triggeringProductIds.add(batch.getProductId());
    }

    private void closeStaleExpirationAlerts(AlertRule rule, List<Product> products, LocalDate today, int days) {
        Set<UUID> stillTriggering = products.stream()
                .filter(p -> p.getExpirationDate() != null)
                .filter(p -> ChronoUnit.DAYS.between(today, p.getExpirationDate()) <= days)
                .map(Product::getId)
                .collect(Collectors.toSet());

        StaleAlertCleanup.resolveStaleAlerts(alertInstanceRepository, rule, stillTriggering, rule.getTenantId());
        log.info("Resolved stale expiration alerts for rule {}", rule.getId());
    }

    private void saveInstance(AlertTriggeredEvent event, AlertRule rule) {
        AlertInstance instance = new AlertInstance(
                rule.getTenantId(),
                event.alertRuleId(),
                event.productId(),
                event.batchId(),
                AlertSeverity.valueOf(event.severity()),
                event.message()
        );
        alertInstanceRepository.save(instance);
    }
}
