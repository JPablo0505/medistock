package com.medistock.alerts.domain.port;

import com.medistock.alerts.domain.model.AlertInstance;
import com.medistock.alerts.domain.model.AlertInstanceStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertInstanceRepository {
    AlertInstance save(AlertInstance instance);
    Optional<AlertInstance> findById(UUID id);
    List<AlertInstance> findByProductId(UUID productId);
    List<AlertInstance> findByStatus(String tenantId, AlertInstanceStatus status);
    List<AlertInstance> findAll(String tenantId, int page, int size);
    long countByStatus(String tenantId, AlertInstanceStatus status);
    boolean existsActiveForRuleAndProduct(UUID alertRuleId, UUID productId);
    List<AlertInstance> findActiveByRuleAndProduct(UUID alertRuleId, UUID productId);
    long deleteResolvedBefore(LocalDateTime cutoff);
}
