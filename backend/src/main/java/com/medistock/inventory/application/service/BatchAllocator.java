package com.medistock.inventory.application.service;

import com.medistock.inventory.domain.model.*;
import com.medistock.inventory.domain.service.FefoAllocationStrategy;
import com.medistock.inventory.domain.service.FefoQuerySpecification;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.inventory.domain.port.MovementRepository;
import com.medistock.crosscutting.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Application service that orchestrates FEFO batch allocation.
 * <p>
 * Handles concurrent access via {@link Retryable} with exponential backoff
 * on optimistic locking failures.
 * </p>
 */
@Service
@Transactional
public class BatchAllocator {

    private static final Logger log = LoggerFactory.getLogger(BatchAllocator.class);

    private final FefoQuerySpecification fefoQuery;
    private final BatchRepository batchRepository;
    private final MovementRepository movementRepository;
    private final FefoAllocationStrategy allocationStrategy;

    public BatchAllocator(FefoQuerySpecification fefoQuery,
                           BatchRepository batchRepository,
                           MovementRepository movementRepository) {
        this.fefoQuery = fefoQuery;
        this.batchRepository = batchRepository;
        this.movementRepository = movementRepository;
        this.allocationStrategy = new FefoAllocationStrategy();
    }

    /**
     * Allocate stock for a sale following FEFO order.
     * <p>
     * Retries up to 3 times with exponential backoff on optimistic locking failures
     * (concurrent allocation from the same batch).
     * </p>
     *
     * @param productId         the product to allocate
     * @param requestedQuantity quantity to allocate
     * @return allocation result with batch breakdown
     * @throws StockShortageException if insufficient stock
     */
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2.0, maxDelay = 500)
    )
    public AllocationResult allocate(UUID productId, int requestedQuantity) {
        String tenantId = resolveTenantId();
        List<Batch> availableBatches = fefoQuery.findAvailableByProductOrderedByExpiry(productId);
        AllocationResult result = allocationStrategy.allocate(availableBatches, productId, requestedQuantity);

        Map<UUID, Batch> batchMap = availableBatches.stream()
                .collect(Collectors.toMap(Batch::getId, b -> b));
        persistAllocations(result, batchMap, productId, tenantId);

        log.info("FEFO allocation: product={}, requested={}, allocated={}, lines={}",
                productId, requestedQuantity, result.totalAllocated(), result.lines().size());
        return result;
    }

    private String resolveTenantId() {
        String tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : "default";
    }

    private void persistAllocations(AllocationResult result, Map<UUID, Batch> batchMap,
                                     UUID productId, String tenantId) {
        for (AllocationLine line : result.lines()) {
            Batch modified = batchMap.get(line.batchId());
            if (modified == null) {
                log.warn("Batch {} not found in allocated set", line.batchId());
                continue;
            }
            batchRepository.save(modified);
            movementRepository.save(Movement.create(
                    tenantId, modified.getId(), productId, MovementType.ALLOCATED,
                    -line.quantityAllocated(),
                    modified.getCurrentQuantity() + line.quantityAllocated(),
                    modified.getCurrentQuantity(),
                    "FEFO allocation for sale", "admin-user"));
        }
    }
}
