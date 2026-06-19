package com.medistock.alerts.application.service;

import com.medistock.alerts.domain.event.AlertTriggeredEvent;
import com.medistock.alerts.domain.model.*;
import com.medistock.alerts.domain.port.AlertInstanceRepository;
import com.medistock.alerts.domain.port.AlertRuleRepository;
import com.medistock.inventory.domain.model.Batch;
import com.medistock.inventory.domain.model.BatchStatus;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
public class EvaluateAlertRulesUseCase {

    private static final Logger log = LoggerFactory.getLogger(EvaluateAlertRulesUseCase.class);

    private final AlertRuleRepository alertRuleRepository;
    private final AlertInstanceRepository alertInstanceRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;

    public EvaluateAlertRulesUseCase(AlertRuleRepository alertRuleRepository,
                                     AlertInstanceRepository alertInstanceRepository,
                                     BatchRepository batchRepository,
                                     ProductRepository productRepository) {
        this.alertRuleRepository = alertRuleRepository;
        this.alertInstanceRepository = alertInstanceRepository;
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
    }

    public List<AlertTriggeredEvent> evaluateAll(String tenantId) {
        List<AlertTriggeredEvent> triggered = new ArrayList<>();
        List<AlertRule> activeRules = alertRuleRepository.findByType(tenantId, null)
                .stream()
                .filter(AlertRule::isActive)
                .toList();

        for (AlertRule rule : activeRules) {
            try {
                triggered.addAll(evaluateRule(tenantId, rule));
            } catch (Exception e) {
                log.error("Error evaluating rule {}: {}", rule.getId(), e.getMessage(), e);
            }
        }
        return triggered;
    }

    public List<AlertTriggeredEvent> evaluateRule(String tenantId, AlertRule rule) {
        return switch (rule.getType()) {
            case EXPIRATION -> evaluateExpirationRule(tenantId, rule);
            case LOW_STOCK -> evaluateLowStockRule(tenantId, rule);
            case CONTROLLED_STOCK -> evaluateControlledStockRule(tenantId, rule);
            case PRESCRIPTION -> List.of();
        };
    }

    private List<AlertTriggeredEvent> evaluateExpirationRule(String tenantId, AlertRule rule) {
        List<AlertTriggeredEvent> events = new ArrayList<>();
        int days = rule.getExpirationDays();
        LocalDate cutoff = LocalDate.now(ZoneId.systemDefault()).plusDays(days);

        List<Batch> batches = batchRepository.findAllByTenantId(tenantId);
        for (Batch batch : batches) {
            if (batch.getStatus() != BatchStatus.ACTIVE) continue;

            LocalDate expDate = batch.getExpirationDate();
            if (expDate != null && !expDate.isAfter(cutoff)) {
                UUID productId = batch.getProductId();
                if (!alertInstanceRepository.existsActiveForRuleAndProduct(rule.getId(), productId)) {
                    String msg = String.format("Batch %s expires on %s (%d days)",
                            batch.getBatchCode(), expDate, days);
                    AlertTriggeredEvent event = new AlertTriggeredEvent(
                            rule.getId(), productId, batch.getId(),
                            msg, rule.getSeverity().name());
                    saveInstance(event);
                    events.add(event);
                }
            }
        }
        return events;
    }

    private List<AlertTriggeredEvent> evaluateLowStockRule(String tenantId, AlertRule rule) {
        List<AlertTriggeredEvent> events = new ArrayList<>();
        int minStock = rule.getMinStock();

        List<Batch> batches = batchRepository.findAllByTenantId(tenantId);
        var productQuantities = new HashMap<String, Integer>();
        for (Batch batch : batches) {
            if (batch.getStatus() != BatchStatus.ACTIVE) continue;
            productQuantities.merge(batch.getProductId().toString(), batch.getCurrentQuantity(), Integer::sum);
        }

        for (var entry : productQuantities.entrySet()) {
            if (entry.getValue() <= minStock) {
                UUID productId = UUID.fromString(entry.getKey());
                if (!alertInstanceRepository.existsActiveForRuleAndProduct(rule.getId(), productId)) {
                    String msg = String.format("Product %s has only %d units remaining (threshold: %d)",
                            entry.getKey(), entry.getValue(), minStock);
                    AlertTriggeredEvent event = new AlertTriggeredEvent(
                            rule.getId(), productId, null, msg, rule.getSeverity().name());
                    saveInstance(event);
                    events.add(event);
                }
            }
        }
        return events;
    }

    private List<AlertTriggeredEvent> evaluateControlledStockRule(String tenantId, AlertRule rule) {
        List<AlertTriggeredEvent> events = new ArrayList<>();
        List<Product> products = productRepository.findByTenantId(tenantId);
        for (Product product : products) {
            if (!product.isControlled()) continue;

            List<Batch> batches = batchRepository.findByProductId(product.getId());
            int totalStock = batches.stream()
                    .filter(b -> b.getStatus() == BatchStatus.ACTIVE)
                    .mapToInt(Batch::getCurrentQuantity)
                    .sum();

            int threshold = rule.getMinStock();
            if (totalStock <= threshold) {
                if (!alertInstanceRepository.existsActiveForRuleAndProduct(
                        rule.getId(), product.getId())) {
                    String msg = String.format("Controlled product %s has %d units (threshold: %d)",
                            product.getName(), totalStock, threshold);
                    AlertTriggeredEvent event = new AlertTriggeredEvent(
                            rule.getId(), product.getId(), null, msg, rule.getSeverity().name());
                    saveInstance(event);
                    events.add(event);
                }
            }
        }
        return events;
    }

    private void saveInstance(AlertTriggeredEvent event) {
        AlertInstance instance = new AlertInstance(
                event.alertRuleId(),
                event.productId(),
                event.batchId(),
                AlertSeverity.valueOf(event.severity()),
                event.message()
        );
        alertInstanceRepository.save(instance);
    }
}
