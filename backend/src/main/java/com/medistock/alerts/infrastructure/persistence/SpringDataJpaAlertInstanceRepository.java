package com.medistock.alerts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SpringDataJpaAlertInstanceRepository extends JpaRepository<JpaAlertInstanceEntity, UUID> {

    List<JpaAlertInstanceEntity> findByProductId(UUID productId);

    List<JpaAlertInstanceEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<JpaAlertInstanceEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, String status);

    @Query("SELECT i FROM JpaAlertInstanceEntity i ORDER BY i.createdAt DESC")
    List<JpaAlertInstanceEntity> findAllOrdered();

    @Query("SELECT COUNT(i) FROM JpaAlertInstanceEntity i WHERE i.status = :status")
    long countByStatus(@Param("status") String status);

    @Query("SELECT COUNT(i) FROM JpaAlertInstanceEntity i WHERE i.tenantId = :tenantId AND i.status = :status")
    long countByTenantIdAndStatus(@Param("tenantId") String tenantId, @Param("status") String status);

    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM JpaAlertInstanceEntity i " +
           "WHERE i.alertRuleId = :ruleId AND i.productId = :productId AND i.status = 'ACTIVE'")
    boolean existsActiveForRuleAndProduct(@Param("ruleId") UUID ruleId,
                                          @Param("productId") UUID productId);

    @Query("SELECT i FROM JpaAlertInstanceEntity i WHERE i.alertRuleId = :ruleId " +
           "AND i.productId = :productId AND i.status = 'ACTIVE'")
    List<JpaAlertInstanceEntity> findActiveByRuleAndProduct(@Param("ruleId") UUID ruleId,
                                                            @Param("productId") UUID productId);

    void deleteByStatusAndResolvedAtBefore(String status, LocalDateTime cutoff);
}
