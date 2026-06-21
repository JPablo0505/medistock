package com.medistock.inventory.domain.port;

import com.medistock.inventory.domain.model.Batch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for Batch aggregate.
 */
public interface BatchRepository {

    Batch save(Batch batch);

    Optional<Batch> findById(UUID id);

    List<Batch> findByProductId(UUID productId);

    List<Batch> findAvailableByProductOrderedByExpiry(UUID productId);

    List<Batch> findAllByTenantId(String tenantId);

    List<Batch> findAll();

    void delete(Batch batch);
}
