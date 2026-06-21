package com.medistock.inventory.application.service;

import com.medistock.inventory.domain.model.*;
import com.medistock.inventory.domain.service.FefoAllocationStrategy;
import com.medistock.inventory.domain.service.FefoQuerySpecification;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.inventory.domain.port.MovementRepository;
import com.medistock.crosscutting.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    public BatchAllocator(FefoQuerySpecification fefoQuery,
                           BatchRepository batchRepository,
                           MovementRepository movementRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.fefoQuery = fefoQuery;
        this.batchRepository = batchRepository;
        this.movementRepository = movementRepository;
        this.allocationStrategy = new FefoAllocationStrategy();
        this.eventPublisher = eventPublisher;
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
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) tenantId = "default";
        String performedBy = "admin-user";

        // 1. Query available batches in FEFO order (PESSIMISTIC_READ applied at JPA level)
        List<Batch> availableBatches = fefoQuery.findAvailableByProductOrderedByExpiry(productId);

        // 2. Run domain strategy — mutates batches in place
        AllocationResult result = allocationStrategy.allocate(availableBatches, productId, requestedQuantity);

        // 3. Build batch lookup map for persistence
        Map<UUID, Batch> batchMap = availableBatches.stream()
                .collect(Collectors.toMap(Batch::getId, b -> b));

        // 4. Persist batch modifications and record movements
        for (AllocationLine line : result.lines()) {
            Batch modified = batchMap.get(line.batchId());
            if (modified == null) {
                log.warn("Batch {} not found in allocated set", line.batchId());
                continue;
            }

            batchRepository.save(modified);

            Movement movement = Movement.record(
                    tenantId,
                    modified.getId(),
                    productId,
                    MovementType.ALLOCATED,
                    -line.quantityAllocated(),
                    modified.getCurrentQuantity() + line.quantityAllocated(),
                    modified.getCurrentQuantity(),
                    "FEFO allocation for sale",
                    performedBy
            );
            movementRepository.save(movement);
        }

        log.info("FEFO allocation: product={}, requested={}, allocated={}, lines={}",
                productId, requestedQuantity, result.totalAllocated(), result.lines().size());

        return result;
    }
}
