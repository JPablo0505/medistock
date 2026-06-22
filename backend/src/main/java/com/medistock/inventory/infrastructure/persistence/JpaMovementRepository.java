package com.medistock.inventory.infrastructure.persistence;

import com.medistock.inventory.domain.model.Movement;
import com.medistock.inventory.domain.model.MovementType;
import com.medistock.inventory.domain.port.MovementRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

/**
 * JPA adapter for MovementRepository (immutable append-only ledger).
 * Manual mapping between domain Movement and JpaMovement (DSG-03).
 */
@Repository
public class JpaMovementRepository implements MovementRepository {

    private final SpringDataJpaMovementRepository springRepo;

    public JpaMovementRepository(SpringDataJpaMovementRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Movement save(Movement movement) {
        JpaMovement jpa = toJpa(movement);
        JpaMovement saved = springRepo.save(jpa);
        return toDomain(saved);
    }

    @Override
    public List<Movement> findByBatchId(UUID batchId) {
        return springRepo.findByBatchIdOrderByCreatedAtAsc(batchId.toString()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Movement> findByProductId(UUID productId) {
        return springRepo.findByProductIdOrderByCreatedAtAsc(productId.toString()).stream()
                .map(this::toDomain)
                .toList();
    }

    // --- Mapping ---

    private Movement toDomain(JpaMovement jpa) {
        return Movement.create(
                jpa.getTenantId(),
                UUID.fromString(jpa.getBatchId()),
                UUID.fromString(jpa.getProductId()),
                MovementType.valueOf(jpa.getType()),
                jpa.getQuantity(),
                jpa.getPreviousQuantity() != null ? jpa.getPreviousQuantity() : 0,
                jpa.getNewQuantity() != null ? jpa.getNewQuantity() : jpa.getQuantity(),
                jpa.getReason(),
                jpa.getPerformedBy()
        );
    }

    private JpaMovement toJpa(Movement movement) {
        JpaMovement jpa = new JpaMovement();
        jpa.setId(movement.getId());
        jpa.setTenantId(movement.getTenantId());
        jpa.setBatchId(movement.getBatchId().toString());
        jpa.setProductId(movement.getProductId().toString());
        jpa.setType(movement.getType().name());
        jpa.setQuantity(movement.getQuantity());
        jpa.setPreviousQuantity(movement.getPreviousQuantity());
        jpa.setNewQuantity(movement.getNewQuantity());
        jpa.setReason(movement.getReason());
        jpa.setPerformedBy(movement.getPerformedBy());
        return jpa;
    }
}
