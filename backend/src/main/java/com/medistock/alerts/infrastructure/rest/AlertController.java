package com.medistock.alerts.infrastructure.rest;

import com.medistock.alerts.application.service.*;
import com.medistock.alerts.domain.model.AlertInstance;
import com.medistock.alerts.domain.model.AlertInstanceStatus;
import com.medistock.alerts.domain.port.AlertInstanceRepository;
import com.medistock.alerts.domain.port.AlertRuleRepository;
import com.medistock.alerts.infrastructure.rest.dto.*;
import com.medistock.crosscutting.security.TenantContext;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.productcatalog.domain.port.ProductRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private static final String DEFAULT_TENANT = "default";

    private static String resolveTenantId(String tenantId) {
        if (tenantId != null) return tenantId;
        String fromContext = TenantContext.getTenantId();
        return fromContext != null ? fromContext : DEFAULT_TENANT;
    }

    private final CreateAlertRuleUseCase createAlertRuleUseCase;
    private final UpdateAlertRuleUseCase updateAlertRuleUseCase;
    private final EvaluateAlertRulesUseCase evaluateAlertRulesUseCase;
    private final AcknowledgeAlertUseCase acknowledgeAlertUseCase;
    private final ResolveAlertUseCase resolveAlertUseCase;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertInstanceRepository alertInstanceRepository;
    private final ProductRepository productRepository;
    private final BatchRepository batchRepository;

    public AlertController(CreateAlertRuleUseCase createAlertRuleUseCase,
                           UpdateAlertRuleUseCase updateAlertRuleUseCase,
                           EvaluateAlertRulesUseCase evaluateAlertRulesUseCase,
                           AcknowledgeAlertUseCase acknowledgeAlertUseCase,
                           ResolveAlertUseCase resolveAlertUseCase,
                           AlertRuleRepository alertRuleRepository,
                           AlertInstanceRepository alertInstanceRepository,
                           ProductRepository productRepository,
                           BatchRepository batchRepository) {
        this.createAlertRuleUseCase = createAlertRuleUseCase;
        this.updateAlertRuleUseCase = updateAlertRuleUseCase;
        this.evaluateAlertRulesUseCase = evaluateAlertRulesUseCase;
        this.acknowledgeAlertUseCase = acknowledgeAlertUseCase;
        this.resolveAlertUseCase = resolveAlertUseCase;
        this.alertRuleRepository = alertRuleRepository;
        this.alertInstanceRepository = alertInstanceRepository;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
    }

    // ─── Alert Rules ────────────────────────────────────────────────────

    @PostMapping("/rules")
    public ResponseEntity<AlertRuleResponse> createRule(
            @Valid @RequestBody CreateAlertRuleRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        tenantId = resolveTenantId(tenantId);
        var rule = createAlertRuleUseCase.execute(
                tenantId, request.name(), request.description(),
                request.type(), request.config());
        return ResponseEntity.created(URI.create("/api/v1/alerts/rules/" + rule.getId()))
                .body(AlertRuleResponse.from(rule));
    }

    @GetMapping("/rules")
    public ResponseEntity<List<AlertRuleResponse>> listRules(
            @RequestParam(required = false) String type,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        tenantId = resolveTenantId(tenantId);
        var rules = alertRuleRepository.findByType(tenantId, type).stream()
                .map(AlertRuleResponse::from)
                .toList();
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<AlertRuleResponse> getRule(@PathVariable UUID id) {
        return alertRuleRepository.findById(id)
                .map(rule -> ResponseEntity.ok(AlertRuleResponse.from(rule)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<AlertRuleResponse> updateRule(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAlertRuleRequest request) {
        var rule = updateAlertRuleUseCase.execute(
                id, request.name(), request.description(),
                request.type(), request.config());
        return ResponseEntity.ok(AlertRuleResponse.from(rule));
    }

    @PostMapping("/rules/{id}/toggle")
    public ResponseEntity<AlertRuleResponse> toggleRule(@PathVariable UUID id) {
        updateAlertRuleUseCase.toggleActive(id);
        return alertRuleRepository.findById(id)
                .map(rule -> ResponseEntity.ok(AlertRuleResponse.from(rule)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        alertRuleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Alert Instances ────────────────────────────────────────────────

    @GetMapping("/instances")
    public ResponseEntity<List<AlertInstanceResponse>> listInstances(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String status,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        tenantId = resolveTenantId(tenantId);
        List<AlertInstance> instances;
        if (status != null) {
            var statusEnum = AlertInstanceStatus.valueOf(status.toUpperCase());
            instances = alertInstanceRepository.findByStatus(tenantId, statusEnum);
        } else {
            instances = alertInstanceRepository.findAll(tenantId, page, size);
        }
        return ResponseEntity.ok(enrichWithProductInfo(instances));
    }

    @GetMapping("/instances/count")
    public ResponseEntity<Long> countActive(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        tenantId = resolveTenantId(tenantId);
        return ResponseEntity.ok(
                alertInstanceRepository.countByStatus(tenantId, AlertInstanceStatus.ACTIVE));
    }

    @PostMapping("/instances/{id}/acknowledge")
    public ResponseEntity<AlertInstanceResponse> acknowledge(@PathVariable UUID id) {
        var instance = acknowledgeAlertUseCase.execute(id);
        return ResponseEntity.ok(withProductInfo(instance));
    }

    @PostMapping("/instances/{id}/resolve")
    public ResponseEntity<AlertInstanceResponse> resolve(@PathVariable UUID id) {
        var instance = resolveAlertUseCase.execute(id);
        return ResponseEntity.ok(withProductInfo(instance));
    }

    // ─── Product enrichment helpers ──────────────────────────────────────

    private List<AlertInstanceResponse> enrichWithProductInfo(List<AlertInstance> instances) {
        // Collect unique product IDs from all instances
        Set<UUID> productIds = instances.stream()
                .map(AlertInstance::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Load products and compute stock for each
        Map<UUID, AlertInstanceResponse.ProductEnrichment> productMap = new HashMap<>();
        for (UUID pid : productIds) {
            productRepository.findById(pid).ifPresent(product -> {
                int stock = computeStockForProduct(product.getId());
                productMap.put(pid, new AlertInstanceResponse.ProductEnrichment(
                        product.getName(),
                        product.getDosageForm(),
                        product.getManufacturer(),
                        stock,
                        product.getExpirationDate()
                ));
            });
        }

        return instances.stream()
                .map(inst -> AlertInstanceResponse.from(inst, productMap.get(inst.getProductId())))
                .toList();
    }

    private AlertInstanceResponse withProductInfo(AlertInstance instance) {
        AlertInstanceResponse.ProductEnrichment enrichment = instance.getProductId() == null
                ? null
                : productRepository.findById(instance.getProductId())
                        .map(p -> new AlertInstanceResponse.ProductEnrichment(
                                p.getName(), p.getDosageForm(), p.getManufacturer(),
                                computeStockForProduct(p.getId()), p.getExpirationDate()))
                        .orElse(null);
        return AlertInstanceResponse.from(instance, enrichment);
    }

    private int computeStockForProduct(UUID productId) {
        return batchRepository.findByProductId(productId).stream()
                .filter(b -> b.getStatus() == com.medistock.inventory.domain.model.BatchStatus.ACTIVE)
                .mapToInt(com.medistock.inventory.domain.model.Batch::getCurrentQuantity)
                .sum();
    }

    // ─── Evaluation ─────────────────────────────────────────────────────

    @PostMapping("/evaluate")
    public ResponseEntity<Integer> evaluate(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        tenantId = resolveTenantId(tenantId);
        var triggered = evaluateAlertRulesUseCase.evaluateAll(tenantId);
        return ResponseEntity.ok(triggered.size());
    }
}
