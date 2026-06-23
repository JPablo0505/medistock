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
public class ControlledStockAlertEvaluator implements AlertRuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ControlledStockAlertEvaluator.class);

    private final AlertInstanceRepository alertInstanceRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;

    public ControlledStockAlertEvaluator(AlertInstanceRepository alertInstanceRepository,
                                          BatchRepository batchRepository,
                                          ProductRepository productRepository) {
        this.alertInstanceRepository = alertInstanceRepository;
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
    }

    @Override
    public AlertRuleType supportedType() {
        return AlertRuleType.CONTROLLED_STOCK;
    }

    @Override
    public List<AlertTriggeredEvent> evaluate(String tenantId, AlertRule rule) {
        List<AlertTriggeredEvent> events = new ArrayList<>();
        List<Product> products = productRepository.findByTenantId(tenantId);
        Set<UUID> triggeringProductIds = new HashSet<>();

        for (Product product : products) {
            if (!product.isControlled()) continue;
            checkControlledProduct(product, rule, triggeringProductIds, events);
        }

        closeStaleAlerts(rule, triggeringProductIds, tenantId);
        return events;
    }

    private void checkControlledProduct(Product product, AlertRule rule,
                                         Set<UUID> triggeringProductIds, List<AlertTriggeredEvent> events) {
        int totalStock = computeStock(product.getId());
        if (totalStock > rule.getMinStock()) return;

        triggeringProductIds.add(product.getId());
        if (alertInstanceRepository.existsActiveForRuleAndProduct(rule.getId(), product.getId())) return;

        String msg = String.format("Controlado %s: %d unidades (mínimo: %d)",
                product.getName(), totalStock, rule.getMinStock());
        AlertTriggeredEvent event = new AlertTriggeredEvent(
                rule.getId(), product.getId(), null, msg, rule.getSeverity().name());
        saveInstance(event, rule);
        events.add(event);
    }

    private int computeStock(UUID productId) {
        return batchRepository.findByProductId(productId).stream()
                .filter(b -> b.getStatus() == BatchStatus.ACTIVE)
                .mapToInt(Batch::getCurrentQuantity)
                .sum();
    }

    private void closeStaleAlerts(AlertRule rule, Set<UUID> triggeringProductIds, String tenantId) {
        try {
            StaleAlertCleanup.resolveStaleAlerts(alertInstanceRepository, rule, triggeringProductIds, tenantId);
        } catch (Exception e) {
            log.warn("Could not close stale alerts for controlled stock rule: {}", e.getMessage());
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
