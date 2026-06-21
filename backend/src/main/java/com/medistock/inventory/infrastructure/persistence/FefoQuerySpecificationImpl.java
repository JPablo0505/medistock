package com.medistock.inventory.infrastructure.persistence;

import com.medistock.inventory.domain.model.Batch;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.inventory.domain.service.FefoQuerySpecification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * JPA-backed implementation of {@link FefoQuerySpecification}.
 * Delegates to the {@link BatchRepository} which already has the FEFO query.
 */
@Service
public class FefoQuerySpecificationImpl implements FefoQuerySpecification {

    private final BatchRepository batchRepository;

    public FefoQuerySpecificationImpl(BatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    @Override
    public List<Batch> findAvailableByProductOrderedByExpiry(UUID productId) {
        return batchRepository.findAvailableByProductOrderedByExpiry(productId);
    }
}
