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
            Set<UUID> tenantProductIds = collectTenantProductIds(tenantId);
            List<AlertInstance> allActive = alertInstanceRepository
                    .findByStatus(tenantId, AlertInstanceStatus.ACTIVE);

            resolveOrphanedAlerts(allActive, tenantProductIds, tenantId);
            deduplicateAlertInstances(allActive);
        } catch (Exception e) {
            log.warn("Could not run orphan cleanup: {}", e.getMessage());
        }
    }

    private Set<UUID> collectTenantProductIds(String tenantId) {
        Set<UUID> ids = productRepository.findByTenantId(tenantId).stream()
                .map(Product::getId)
                .collect(Collectors.toSet());
        batchRepository.findAllByTenantId(tenantId).stream()
                .map(Batch::getProductId)
                .forEach(ids::add);
        return ids;
    }

    private void resolveOrphanedAlerts(List<AlertInstance> alerts, Set<UUID> validProductIds, String tenantId) {
        alerts.stream()
                .filter(a -> a.getProductId() != null && !validProductIds.contains(a.getProductId()))
                .forEach(alert -> {
                    alert.resolve();
                    alertInstanceRepository.save(alert);
                    log.info("Resolved orphaned alert for product {} (not in tenant {})",
                            alert.getProductId(), tenantId);
                });
    }

    private void deduplicateAlertInstances(List<AlertInstance> activeAlerts) {
        record AlertKey(UUID ruleId, UUID productId) {}
        Map<AlertKey, List<AlertInstance>> grouped = activeAlerts.stream()
                .filter(a -> a.getProductId() != null)
                .collect(Collectors.groupingBy(a -> new AlertKey(a.getAlertRuleId(), a.getProductId())));

        grouped.values().stream()
                .filter(list -> list.size() > 1)
                .forEach(dupes -> resolveOlderDuplicates(dupes, dupes.get(0).getAlertRuleId()));
    }

    private void resolveOlderDuplicates(List<AlertInstance> dupes, UUID ruleId) {
        dupes.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        dupes.subList(1, dupes.size()).forEach(alert -> {
            alert.resolve();
            alertInstanceRepository.save(alert);
            log.info("Resolved duplicate alert instance {} for rule={}", alert.getId(), ruleId);
        });
    }

    private void ensureDefaultRules(String tenantId) {
        List<AlertRule> existing = alertRuleRepository.findByType(tenantId, null);
        if (existing.size() > 1) {
            deduplicateRules(existing);
            existing = alertRuleRepository.findByType(tenantId, null);
        }
        if (!existing.isEmpty()) return;

        log.info("No alert rules found for tenant={}, creating defaults", tenantId);
        alertRuleRepository.save(createExpirationRule(tenantId));
        alertRuleRepository.save(createLowStockRule(tenantId));
    }

    private AlertRule createExpirationRule(String tenantId) {
        return new AlertRule(tenantId, "Vencimiento próximo",
                "Alerta cuando un medicamento vence en 30 días o menos",
                AlertRuleType.EXPIRATION, Map.of("expirationDays", 30));
    }

    private AlertRule createLowStockRule(String tenantId) {
        return new AlertRule(tenantId, "Stock bajo",
                "Alerta cuando un medicamento no tiene stock disponible",
                AlertRuleType.LOW_STOCK, Map.of("minStock", 0));
    }

    private void deduplicateRules(List<AlertRule> rules) {
        Map<AlertRuleType, List<AlertRule>> byType = rules.stream()
                .collect(Collectors.groupingBy(AlertRule::getType));

        byType.values().stream()
                .filter(list -> list.size() > 1)
                .forEach(this::keepFirstDeleteRest);
    }

    private void keepFirstDeleteRest(List<AlertRule> sameType) {
        sameType.sort((a, b) -> {
            if (a.isActive() != b.isActive()) return a.isActive() ? -1 : 1;
            return a.getCreatedAt().compareTo(b.getCreatedAt());
        });
        sameType.subList(1, sameType.size()).forEach(rule -> {
            log.info("Deleting duplicate rule {} ({})", rule.getName(), rule.getId());
            alertRuleRepository.deleteById(rule.getId());
        });
    }
}
