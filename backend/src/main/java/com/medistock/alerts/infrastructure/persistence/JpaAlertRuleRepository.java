package com.medistock.alerts.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medistock.alerts.domain.model.AlertRule;
import com.medistock.alerts.domain.model.AlertRuleType;
import com.medistock.alerts.domain.port.AlertRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaAlertRuleRepository implements AlertRuleRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaAlertRuleRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SpringDataJpaAlertRuleRepository springRepo;

    public JpaAlertRuleRepository(SpringDataJpaAlertRuleRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public AlertRule save(AlertRule rule) {
        JpaAlertRuleEntity entity = toEntity(rule);
        JpaAlertRuleEntity saved = springRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<AlertRule> findById(UUID id) {
        return springRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<AlertRule> findAll(String tenantId) {
        return springRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<AlertRule> findByType(String tenantId, String type) {
        if (type == null) return findAll(tenantId);
        return springRepo.findByRuleType(type).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<AlertRule> findActiveByType(String tenantId, String type) {
        return springRepo.findActiveByType(type).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void deleteById(UUID id) {
        springRepo.deleteById(id);
    }

    private JpaAlertRuleEntity toEntity(AlertRule rule) {
        JpaAlertRuleEntity entity = new JpaAlertRuleEntity();
        entity.setId(rule.getId());
        entity.setTenantId(rule.getTenantId());
        entity.setName(rule.getName());
        entity.setDescription(rule.getDescription());
        entity.setRuleType(rule.getType().name());
        entity.setActive(rule.isActive());
        try {
            entity.setConfigJson(MAPPER.writeValueAsString(rule.getConfig()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize config", e);
            entity.setConfigJson("{}");
        }
        entity.setCreatedAt(rule.getCreatedAt());
        entity.setUpdatedAt(rule.getUpdatedAt());
        return entity;
    }

    private AlertRule toDomain(JpaAlertRuleEntity entity) {
        return AlertRule.restore(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getDescription(),
                AlertRuleType.valueOf(entity.getRuleType()),
                parseConfig(entity.getConfigJson()),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse config JSON", e);
            return Map.of();
        }
    }
}
