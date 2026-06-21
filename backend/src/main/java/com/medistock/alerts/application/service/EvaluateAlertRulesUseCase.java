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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        // ── Check products for expiration ────────────────────────────────
        List<Product> products = productRepository.findByTenantId(tenantId);
        log.debug("Expiration check: found {} products for tenant={}", products.size(), tenantId);
        Set<UUID> triggeringProductIds = new HashSet<>();

        for (Product product : products) {
            LocalDate expDate = product.getExpirationDate();
            if (expDate == null) continue;

            long daysUntilExpiry = ChronoUnit.DAYS.between(today, expDate);

            if (daysUntilExpiry <= days) {
                triggeringProductIds.add(product.getId());

                if (!alertInstanceRepository.existsActiveForRuleAndProduct(rule.getId(), product.getId())) {
                    String msg;
                    if (daysUntilExpiry < 0) {
                        msg = String.format("Medicamento %s venció hace %d días (%s)",
                                product.getName(), Math.abs(daysUntilExpiry), expDate);
                    } else {
                        msg = String.format("Medicamento %s vence en %d días (%s)",
                                product.getName(), daysUntilExpiry, expDate);
                    }
                    AlertTriggeredEvent event = new AlertTriggeredEvent(
                            rule.getId(), product.getId(), null,
                            msg, rule.getSeverity().name());
                    saveInstance(event, rule);
                    events.add(event);
                    log.info("Expiration alert: product={}, expires={}, daysUntil={}",
                            product.getName(), expDate, daysUntilExpiry);
                }
            }
        }

        // ── Also check batch-level expiration ─────────────────────────────
        List<Batch> batches = batchRepository.findAllByTenantId(tenantId);
        for (Batch batch : batches) {
            if (batch.getStatus() != BatchStatus.ACTIVE) continue;

            LocalDate expDate = batch.getExpirationDate();
            if (expDate == null) continue;

            long daysUntilBatchExpiry = ChronoUnit.DAYS.between(today, expDate);
            if (daysUntilBatchExpiry <= days && !triggeringProductIds.contains(batch.getProductId())) {
                if (!alertInstanceRepository.existsActiveForRuleAndProduct(rule.getId(), batch.getProductId())) {
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
            }
        }

        // ── Close stale alerts for products no longer triggering ─────────
        closeStaleExpirationAlerts(rule, products, today, days);

        return events;
    }

    private void closeStaleExpirationAlerts(AlertRule rule, List<Product> products, LocalDate today, int days) {
        Set<UUID> stillTriggering = products.stream()
                .filter(p -> p.getExpirationDate() != null)
                .filter(p -> ChronoUnit.DAYS.between(today, p.getExpirationDate()) <= days)
                .map(Product::getId)
                .collect(Collectors.toSet());

        List<AlertInstance> activeAlerts = alertInstanceRepository
                .findByStatus(rule.getTenantId(), AlertInstanceStatus.ACTIVE);

        for (AlertInstance alert : activeAlerts) {
            if (alert.getAlertRuleId().equals(rule.getId())
                    && alert.getProductId() != null
                    && !stillTriggering.contains(alert.getProductId())) {
                alert.resolve();
                alertInstanceRepository.save(alert);
                log.info("Resolved stale expiration alert for product {}", alert.getProductId());
            }
        }
    }

    private List<AlertTriggeredEvent> evaluateLowStockRule(String tenantId, AlertRule rule) {
        List<AlertTriggeredEvent> events = new ArrayList<>();
        int minStock = rule.getMinStock();

        // ── Compute stock from ACTIVE batches per product ────────────────
        List<Batch> batches = batchRepository.findAllByTenantId(tenantId);
        Map<UUID, Integer> productQuantities = new HashMap<>();
        for (Batch batch : batches) {
            if (batch.getStatus() != BatchStatus.ACTIVE) continue;
            productQuantities.merge(batch.getProductId(), batch.getCurrentQuantity(), Integer::sum);
        }

        log.debug("Low stock check: {} active batches, {} products with stock for tenant={}",
                batches.size(), productQuantities.size(), tenantId);

        // ── Check products ───────────────────────────────────────────────
        List<Product> products = productRepository.findByTenantId(tenantId);
        Set<UUID> triggeringProductIds = new HashSet<>();

        for (Product product : products) {
            int totalStock = productQuantities.getOrDefault(product.getId(), 0);

            if (totalStock <= minStock) {
                triggeringProductIds.add(product.getId());

                if (!alertInstanceRepository.existsActiveForRuleAndProduct(rule.getId(), product.getId())) {
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
            }
        }

        // ── Close stale alerts for products now above threshold ──────────
        closeStaleLowStockAlerts(rule, triggeringProductIds);

        return events;
    }

    private void closeStaleLowStockAlerts(AlertRule rule, Set<UUID> stillTriggering) {
        try {
            List<AlertInstance> allActive = alertInstanceRepository
                    .findByStatus(rule.getTenantId(), AlertInstanceStatus.ACTIVE);
            for (AlertInstance alert : allActive) {
                if (alert.getAlertRuleId().equals(rule.getId())
                        && alert.getProductId() != null
                        && !stillTriggering.contains(alert.getProductId())) {
                    alert.resolve();
                    alertInstanceRepository.save(alert);
                    log.info("Resolved stale low-stock alert for product {}", alert.getProductId());
                }
            }
        } catch (Exception e) {
            log.warn("Could not close stale alerts for rule {}: {}", rule.getId(), e.getMessage());
        }
    }

    private List<AlertTriggeredEvent> evaluateControlledStockRule(String tenantId, AlertRule rule) {
        List<AlertTriggeredEvent> events = new ArrayList<>();
        List<Product> products = productRepository.findByTenantId(tenantId);
        Set<UUID> triggeringProductIds = new HashSet<>();

        for (Product product : products) {
            if (!product.isControlled()) continue;

            List<Batch> productBatches = batchRepository.findByProductId(product.getId());
            int totalStock = productBatches.stream()
                    .filter(b -> b.getStatus() == BatchStatus.ACTIVE)
                    .mapToInt(Batch::getCurrentQuantity)
                    .sum();

            int threshold = rule.getMinStock();
            if (totalStock <= threshold) {
                triggeringProductIds.add(product.getId());
                if (!alertInstanceRepository.existsActiveForRuleAndProduct(
                        rule.getId(), product.getId())) {
                    String msg = String.format("Controlado %s: %d unidades (mínimo: %d)",
                            product.getName(), totalStock, threshold);
                    AlertTriggeredEvent event = new AlertTriggeredEvent(
                            rule.getId(), product.getId(), null, msg, rule.getSeverity().name());
                    saveInstance(event, rule);
                    events.add(event);
                }
            }
        }

        // Close stale controlled stock alerts
        try {
            List<AlertInstance> allActive = alertInstanceRepository
                    .findByStatus(tenantId, AlertInstanceStatus.ACTIVE);
            for (AlertInstance alert : allActive) {
                if (alert.getAlertRuleId().equals(rule.getId())
                        && alert.getProductId() != null
                        && !triggeringProductIds.contains(alert.getProductId())) {
                    alert.resolve();
                    alertInstanceRepository.save(alert);
                }
            }
        } catch (Exception e) {
            log.warn("Could not close stale alerts for controlled stock rule: {}", e.getMessage());
        }

        return events;
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
