package com.medistock.alerts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpringDataJpaAlertRuleRepository extends JpaRepository<JpaAlertRuleEntity, UUID> {

    List<JpaAlertRuleEntity> findAllByOrderByCreatedAtDesc();

    List<JpaAlertRuleEntity> findByRuleType(String ruleType);

    @Query("SELECT r FROM JpaAlertRuleEntity r WHERE r.ruleType = :type AND r.isActive = true")
    List<JpaAlertRuleEntity> findActiveByType(@Param("type") String type);
}
