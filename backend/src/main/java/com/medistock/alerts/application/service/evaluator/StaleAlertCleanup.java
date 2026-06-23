package com.medistock.alerts.application.service.evaluator;

import com.medistock.alerts.domain.model.AlertInstance;
import com.medistock.alerts.domain.model.AlertInstanceStatus;
import com.medistock.alerts.domain.model.AlertRule;
import com.medistock.alerts.domain.port.AlertInstanceRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Shared utility for resolving stale alert instances that no longer match
 * triggering conditions after re-evaluation.
 */
final class StaleAlertCleanup {

    private StaleAlertCleanup() {}

    /**
     * Resolves all ACTIVE alerts for the given rule whose product IDs are
     * NOT in the {@code stillTriggering} set.
     */
    static void resolveStaleAlerts(AlertInstanceRepository repository, AlertRule rule,
                                    Set<UUID> stillTriggering, String tenantId) {
        List<AlertInstance> allActive = repository.findByStatus(tenantId, AlertInstanceStatus.ACTIVE);
        for (AlertInstance alert : allActive) {
            if (alert.getAlertRuleId().equals(rule.getId())
                    && alert.getProductId() != null
                    && !stillTriggering.contains(alert.getProductId())) {
                alert.resolve();
                repository.save(alert);
            }
        }
    }
}
