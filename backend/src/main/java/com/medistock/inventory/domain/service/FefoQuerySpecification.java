package com.medistock.inventory.domain.service;

import com.medistock.inventory.domain.model.Batch;
import java.util.List;
import java.util.UUID;

/**
 * FEFO (First Expiry, First Out) query specification.
 * Returns batches ordered by expiry date ascending for allocation purposes.
 */
@FunctionalInterface
public interface FefoQuerySpecification {

    /**
     * Find all ACTIVE batches for the given product, ordered by expiry ascending,
     * then by available quantity ascending.
     */
    List<Batch> findAvailableByProductOrderedByExpiry(UUID productId);
}
