package com.medistock.alerts.application.service;

import com.medistock.alerts.domain.event.AlertTriggeredEvent;
import com.medistock.alerts.domain.model.*;
import com.medistock.alerts.domain.port.AlertInstanceRepository;
import com.medistock.alerts.domain.port.AlertRuleRepository;
import com.medistock.alerts.domain.service.AlertRuleEvaluator;
import com.medistock.inventory.domain.model.Batch;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class EvaluateAlertRulesUseCase {

    private static final Logger log = LoggerFactory.getLogger(EvaluateAlertRulesUseCase.class);

    private final AlertRuleRepository alertRuleRepository;
    private final AlertInstanceRepository alertInstanceRepository;
    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final List<AlertRuleEvaluator> evaluators;

    public EvaluateAlertRulesUseCase(AlertRuleRepository alertRuleRepository,
                                      AlertInstanceRepository alertInstanceRepository,
                                      BatchRepository batchRepository,
                                      ProductRepository productRepository,
                                      List<AlertRuleEvaluator> evaluators) {
        this.alertRuleRepository = alertRuleRepository;
        this.alertInstanceRepository = alertInstanceRepository;
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.evaluators = evaluators;
    }

    public List<AlertTriggeredEvent> evaluateAll(String tenantId) {
        List<AlertTriggeredEvent> triggered = new ArrayList<>();

        // Ensure default rules exist if none are present
        ensureDefaultRules(tenantId);

        // ── Cleanup: resolve alerts for products not in this tenant ──────
        cleanupOrphanedAlerts(tenantId);

        List<AlertRule> activeRules = alertRuleRepository.findByType(tenantId, null)
                .stream()
                .filter(AlertRule::isActive)
                .toList();

        log.info("Evaluating {} active rules for tenant={}", activeRules.size(), tenantId);

        for (AlertRule rule : activeRules) {
            try {
                List<AlertTriggeredEvent> events = evaluateRule(tenantId, rule);
                triggered.addAll(events);
                log.info("Rule {} ({}) triggered {} alerts", rule.getName(), rule.getType(), events.size());
            } catch (Exception e) {
                log.error("Error evaluating rule {}: {}", rule.getId(), e.getMessage(), e);
            }
        }
        log.info("Total alerts triggered for tenant={}: {}", tenantId, triggered.size());
        return triggered;
    }

    public List<AlertTriggeredEvent> evaluateRule(String tenantId, AlertRule rule) {
        return evaluators.stream()
                .filter(e -> e.supportedType() == rule.getType())
                .findFirst()
                .map(e -> e.evaluate(tenantId, rule))
                .orElse(List.of());
    }

    private void cleanupOrphanedAlerts(String tenantId) {
        try {
            // Get all product IDs that belong to this tenant
            Set<UUID> tenantProductIds = productRepository.findByTenantId(tenantId).stream()
                    .map(Product::getId)
                    .collect(Collectors.toSet());

            // Also get product IDs from batches in this tenant
            Set<UUID> tenantBatchProductIds = batchRepository.findAllByTenantId(tenantId).stream()
                    .map(Batch::getProductId)
                    .collect(Collectors.toSet());
            tenantProductIds.addAll(tenantBatchProductIds);

            // Resolve active alerts for products NOT in this tenant
            List<AlertInstance> allActive = alertInstanceRepository
                    .findByStatus(tenantId, AlertInstanceStatus.ACTIVE);
            for (AlertInstance alert : allActive) {
                if (alert.getProductId() != null && !tenantProductIds.contains(alert.getProductId())) {
                    alert.resolve();
                    alertInstanceRepository.save(alert);
                    log.info("Resolved orphaned alert for product {} (not in tenant {})",
                            alert.getProductId(), tenantId);
                }
            }

            // ── Deduplicate: resolve older ACTIVE alerts for same rule+product ──
            deduplicateAlertInstances(allActive);
        } catch (Exception e) {
            log.warn("Could not run orphan cleanup: {}", e.getMessage());
        }
    }

    private void deduplicateAlertInstances(List<AlertInstance> activeAlerts) {
        // Group by (ruleId, productId) — keep the newest, resolve the rest
        record AlertKey(UUID ruleId, UUID productId) {}
        Map<AlertKey, List<AlertInstance>> grouped = new HashMap<>();
        for (AlertInstance alert : activeAlerts) {
            if (alert.getProductId() == null) continue;
            AlertKey key = new AlertKey(alert.getAlertRuleId(), alert.getProductId());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(alert);
        }

        for (Map.Entry<AlertKey, List<AlertInstance>> entry : grouped.entrySet()) {
            List<AlertInstance> dupes = entry.getValue();
            if (dupes.size() <= 1) continue;

            // Sort by createdAt DESC — keep newest
            dupes.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            for (int i = 1; i < dupes.size(); i++) {
                dupes.get(i).resolve();
                alertInstanceRepository.save(dupes.get(i));
                log.info("Resolved duplicate alert instance {} for rule={} product={}",
                        dupes.get(i).getId(), entry.getKey().ruleId(), entry.getKey().productId());
            }
        }
    }

    private void ensureDefaultRules(String tenantId) {
        List<AlertRule> existing = alertRuleRepository.findByType(tenantId, null);

        // ── Deduplicate: keep only one active rule per type ──────────────
        if (existing.size() > 1) {
            deduplicateRules(existing);
            existing = alertRuleRepository.findByType(tenantId, null);
        }

        if (!existing.isEmpty()) return;

        log.info("No alert rules found for tenant={}, creating defaults", tenantId);

        // Expiration rule: alert for products expiring within 30 days
        AlertRule expirationRule = new AlertRule(
                tenantId,
                "Vencimiento próximo",
                "Alerta cuando un medicamento vence en 30 días o menos",
                AlertRuleType.EXPIRATION,
                Map.of("expirationDays", 30)
        );
        alertRuleRepository.save(expirationRule);

        // Low stock rule: alert when stock is 0
        AlertRule lowStockRule = new AlertRule(
                tenantId,
                "Stock bajo",
                "Alerta cuando un medicamento no tiene stock disponible",
                AlertRuleType.LOW_STOCK,
                Map.of("minStock", 0)
        );
        alertRuleRepository.save(lowStockRule);
    }

    private void deduplicateRules(List<AlertRule> rules) {
        // Group by type, keep the first active (or first overall), delete the rest
        Map<AlertRuleType, List<AlertRule>> byType = rules.stream()
                .collect(Collectors.groupingBy(AlertRule::getType));

        for (Map.Entry<AlertRuleType, List<AlertRule>> entry : byType.entrySet()) {
            List<AlertRule> sameType = entry.getValue();
            if (sameType.size() <= 1) continue;

            // Sort: active first, then by creation date
            sameType.sort((a, b) -> {
                if (a.isActive() != b.isActive()) return a.isActive() ? -1 : 1;
                return a.getCreatedAt().compareTo(b.getCreatedAt());
            });

            // Keep the first, delete the rest
            AlertRule keep = sameType.get(0);
            for (int i = 1; i < sameType.size(); i++) {
                log.info("Deleting duplicate rule {} ({}) — keeping {}",
                        sameType.get(i).getName(), sameType.get(i).getId(), keep.getId());
                alertRuleRepository.deleteById(sameType.get(i).getId());
            }
        }
    }
}
