package com.medistock.alerts.infrastructure.rest;

import com.medistock.alerts.application.service.*;
import com.medistock.alerts.domain.model.AlertInstanceStatus;
import com.medistock.alerts.domain.port.AlertInstanceRepository;
import com.medistock.alerts.domain.port.AlertRuleRepository;
import com.medistock.alerts.infrastructure.rest.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final CreateAlertRuleUseCase createAlertRuleUseCase;
    private final UpdateAlertRuleUseCase updateAlertRuleUseCase;
    private final EvaluateAlertRulesUseCase evaluateAlertRulesUseCase;
    private final AcknowledgeAlertUseCase acknowledgeAlertUseCase;
    private final ResolveAlertUseCase resolveAlertUseCase;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertInstanceRepository alertInstanceRepository;

    public AlertController(CreateAlertRuleUseCase createAlertRuleUseCase,
                           UpdateAlertRuleUseCase updateAlertRuleUseCase,
                           EvaluateAlertRulesUseCase evaluateAlertRulesUseCase,
                           AcknowledgeAlertUseCase acknowledgeAlertUseCase,
                           ResolveAlertUseCase resolveAlertUseCase,
                           AlertRuleRepository alertRuleRepository,
                           AlertInstanceRepository alertInstanceRepository) {
        this.createAlertRuleUseCase = createAlertRuleUseCase;
        this.updateAlertRuleUseCase = updateAlertRuleUseCase;
        this.evaluateAlertRulesUseCase = evaluateAlertRulesUseCase;
        this.acknowledgeAlertUseCase = acknowledgeAlertUseCase;
        this.resolveAlertUseCase = resolveAlertUseCase;
        this.alertRuleRepository = alertRuleRepository;
        this.alertInstanceRepository = alertInstanceRepository;
    }

    // ─── Alert Rules ────────────────────────────────────────────────────

    @PostMapping("/rules")
    public ResponseEntity<AlertRuleResponse> createRule(
            @Valid @RequestBody CreateAlertRuleRequest request,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        var rule = createAlertRuleUseCase.execute(
                tenantId, request.name(), request.description(),
                request.type(), request.config());
        return ResponseEntity.created(URI.create("/api/v1/alerts/rules/" + rule.getId()))
                .body(AlertRuleResponse.from(rule));
    }

    @GetMapping("/rules")
    public ResponseEntity<List<AlertRuleResponse>> listRules(
            @RequestParam(required = false) String type,
            @RequestHeader("X-Tenant-Id") String tenantId) {
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
            @RequestHeader("X-Tenant-Id") String tenantId) {
        List<AlertInstanceResponse> instances;
        if (status != null) {
            var statusEnum = AlertInstanceStatus.valueOf(status.toUpperCase());
            instances = alertInstanceRepository.findByStatus(tenantId, statusEnum).stream()
                    .map(AlertInstanceResponse::from)
                    .toList();
        } else {
            instances = alertInstanceRepository.findAll(tenantId, page, size).stream()
                    .map(AlertInstanceResponse::from)
                    .toList();
        }
        return ResponseEntity.ok(instances);
    }

    @GetMapping("/instances/count")
    public ResponseEntity<Long> countActive(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(
                alertInstanceRepository.countByStatus(tenantId, AlertInstanceStatus.ACTIVE));
    }

    @PostMapping("/instances/{id}/acknowledge")
    public ResponseEntity<AlertInstanceResponse> acknowledge(@PathVariable UUID id) {
        var instance = acknowledgeAlertUseCase.execute(id);
        return ResponseEntity.ok(AlertInstanceResponse.from(instance));
    }

    @PostMapping("/instances/{id}/resolve")
    public ResponseEntity<AlertInstanceResponse> resolve(@PathVariable UUID id) {
        var instance = resolveAlertUseCase.execute(id);
        return ResponseEntity.ok(AlertInstanceResponse.from(instance));
    }

    // ─── Evaluation ─────────────────────────────────────────────────────

    @PostMapping("/evaluate")
    public ResponseEntity<Integer> evaluate(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        var triggered = evaluateAlertRulesUseCase.evaluateAll(tenantId);
        return ResponseEntity.ok(triggered.size());
    }
}
