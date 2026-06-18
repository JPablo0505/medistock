package com.medistock.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for JpaMovement.
 */
public interface SpringDataJpaMovementRepository extends JpaRepository<JpaMovement, UUID> {

    List<JpaMovement> findByBatchIdOrderByCreatedAtAsc(String batchId);

    List<JpaMovement> findByProductIdOrderByCreatedAtAsc(String productId);
}
