package com.medistock.inventory.domain.port;

import com.medistock.inventory.domain.model.Movement;
import java.util.List;
import java.util.UUID;

/**
 * Repository port for Movement (immutable append-only ledger).
 */
public interface MovementRepository {

    Movement save(Movement movement);

    List<Movement> findByBatchId(UUID batchId);

    List<Movement> findByProductId(UUID productId);
}
