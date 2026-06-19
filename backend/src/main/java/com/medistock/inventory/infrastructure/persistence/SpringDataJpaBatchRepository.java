package com.medistock.inventory.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for JpaBatch.
 */
public interface SpringDataJpaBatchRepository extends JpaRepository<JpaBatch, UUID> {

    List<JpaBatch> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<JpaBatch> findByProductIdOrderByExpirationDateAsc(String productId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT b FROM JpaBatch b WHERE b.productId = :productId " +
           "AND b.status = 'ACTIVE' AND b.currentQuantity > 0 " +
           "ORDER BY b.expirationDate ASC, b.currentQuantity ASC")
    List<JpaBatch> findAvailableFefo(@Param("productId") String productId);
}
