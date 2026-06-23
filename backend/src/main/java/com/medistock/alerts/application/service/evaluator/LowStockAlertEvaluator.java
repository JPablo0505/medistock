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

import java.util.*;

@Component
public class LowStockAlertEvaluator implements AlertRuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(LowStockAlertEvaluator.class);

    private final AlertInstanceRepository alertInstanceRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;

    public LowStockAlertEvaluator(AlertInstanceRepository alertInstanceRepository,
                                   BatchRepository batchRepository,
                                   ProductRepository productRepository) {
        this.alertInstanceRepository = alertInstanceRepository;
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
    }

    @Override
    public AlertRuleType supportedType() {
        return AlertRuleType.LOW_STOCK;
    }

    @Override
    public List<AlertTriggeredEvent> evaluate(String tenantId, AlertRule rule) {
        List<AlertTriggeredEvent> events = new ArrayList<>();
        int minStock = rule.getMinStock();

        // ── Compute stock from ACTIVE batches per product ────────────────
        Map<UUID, Integer> productQuantities = computeStockPerProduct(tenantId);

        log.debug("Low stock check: {} products with stock for tenant={}",
                productQuantities.size(), tenantId);

        // ── Check products ───────────────────────────────────────────────
        List<Product> products = productRepository.findByTenantId(tenantId);
        Set<UUID> triggeringProductIds = new HashSet<>();

        for (Product product : products) {
            checkProductLowStock(rule, product, productQuantities, minStock, triggeringProductIds, events);
        }

        // ── Close stale alerts for products now above threshold ──────────
        closeStaleLowStockAlerts(rule, triggeringProductIds);

        return events;
    }

    private Map<UUID, Integer> computeStockPerProduct(String tenantId) {
        List<Batch> batches = batchRepository.findAllByTenantId(tenantId);
        Map<UUID, Integer> productQuantities = new HashMap<>();
        for (Batch batch : batches) {
            if (batch.getStatus() != BatchStatus.ACTIVE) continue;
            productQuantities.merge(batch.getProductId(), batch.getCurrentQuantity(), Integer::sum);
        }
        return productQuantities;
    }

    private void checkProductLowStock(AlertRule rule, Product product, Map<UUID, Integer> productQuantities,
                                      int minStock, Set<UUID> triggeringProductIds,
                                      List<AlertTriggeredEvent> events) {
        int totalStock = productQuantities.getOrDefault(product.getId(), 0);

        if (totalStock > minStock) return;

        triggeringProductIds.add(product.getId());

        if (alertInstanceRepository.existsActiveForRuleAndProduct(rule.getId(), product.getId())) return;

        String productName = product.getName();
        String msg = totalStock == 0
                ? String.format("Medicamento %s NO tiene stock disponible", productName)
                : String.format("Medicamento %s tiene solo %d unidades (mínimo: %d)",
                productName, totalStock, minStock);

        AlertSeverity severity = totalStock == 0 ? AlertSeverity.HIGH : rule.getSeverity();
        AlertTriggeredEvent event = new AlertTriggeredEvent(
                rule.getId(), product.getId(), null,
                msg, severity.name());
        saveInstance(event, rule);
        events.add(event);
        log.info("Low stock alert: product={}, stock={}, threshold={}",
                productName, totalStock, minStock);
    }

    private void closeStaleLowStockAlerts(AlertRule rule, Set<UUID> stillTriggering) {
        try {
            StaleAlertCleanup.resolveStaleAlerts(alertInstanceRepository, rule, stillTriggering, rule.getTenantId());
            log.info("Resolved stale low-stock alerts for rule {}", rule.getId());
        } catch (Exception e) {
            log.warn("Could not close stale alerts for rule {}: {}", rule.getId(), e.getMessage());
        }
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
