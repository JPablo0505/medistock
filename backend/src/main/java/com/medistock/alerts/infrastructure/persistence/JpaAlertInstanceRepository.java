package com.medistock.alerts.infrastructure.persistence;

import com.medistock.alerts.domain.model.AlertInstance;
import com.medistock.alerts.domain.model.AlertInstanceStatus;
import com.medistock.alerts.domain.model.AlertSeverity;
import com.medistock.alerts.domain.port.AlertInstanceRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaAlertInstanceRepository implements AlertInstanceRepository {

    private final SpringDataJpaAlertInstanceRepository springRepo;

    public JpaAlertInstanceRepository(SpringDataJpaAlertInstanceRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public AlertInstance save(AlertInstance instance) {
        JpaAlertInstanceEntity entity = toEntity(instance);
        JpaAlertInstanceEntity saved = springRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<AlertInstance> findById(UUID id) {
        return springRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<AlertInstance> findByProductId(UUID productId) {
        return springRepo.findByProductId(productId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<AlertInstance> findByStatus(String tenantId, AlertInstanceStatus status) {
        return springRepo.findByStatusOrderByCreatedAtDesc(status.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<AlertInstance> findAll(String tenantId, int page, int size) {
        return springRepo.findAllOrdered().stream()
                .skip((long) page * size)
                .limit(size)
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByStatus(String tenantId, AlertInstanceStatus status) {
        return springRepo.countByStatus(status.name());
    }

    @Override
    public boolean existsActiveForRuleAndProduct(UUID alertRuleId, UUID productId) {
        return springRepo.existsActiveForRuleAndProduct(alertRuleId, productId);
    }

    @Override
    public List<AlertInstance> findActiveByRuleAndProduct(UUID alertRuleId, UUID productId) {
        return springRepo.findActiveByRuleAndProduct(alertRuleId, productId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long deleteResolvedBefore(LocalDateTime cutoff) {
        springRepo.deleteByStatusAndResolvedAtBefore("RESOLVED", cutoff);
        return 0;
    }

    private JpaAlertInstanceEntity toEntity(AlertInstance instance) {
        JpaAlertInstanceEntity entity = new JpaAlertInstanceEntity();
        entity.setId(instance.getId());
        entity.setAlertRuleId(instance.getAlertRuleId());
        entity.setProductId(instance.getProductId());
        entity.setBatchId(instance.getBatchId());
        entity.setSeverity(instance.getSeverity().name());
        entity.setMessage(instance.getMessage());
        entity.setStatus(instance.getStatus().name());
        entity.setAcknowledgedAt(instance.getAcknowledgedAt());
        entity.setResolvedAt(instance.getResolvedAt());
        entity.setCreatedAt(instance.getCreatedAt());
        entity.setUpdatedAt(instance.getUpdatedAt());
        return entity;
    }

    private AlertInstance toDomain(JpaAlertInstanceEntity entity) {
        return AlertInstance.restore(
                entity.getId(),
                entity.getAlertRuleId(),
                entity.getProductId(),
                entity.getBatchId(),
                AlertSeverity.valueOf(entity.getSeverity()),
                entity.getMessage(),
                AlertInstanceStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getAcknowledgedAt(),
                entity.getResolvedAt()
        );
    }
}
