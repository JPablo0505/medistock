package com.medistock.inventory.application.service;

import com.medistock.inventory.domain.model.*;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.inventory.domain.port.MovementRepository;
import com.medistock.crosscutting.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use case: Release a batch from quarantine back to active.
 */
@Service
@Transactional
public class ReleaseFromQuarantineUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReleaseFromQuarantineUseCase.class);

    private final BatchRepository batchRepository;
    private final MovementRepository movementRepository;

    public ReleaseFromQuarantineUseCase(BatchRepository batchRepository,
                                         MovementRepository movementRepository) {
        this.batchRepository = batchRepository;
        this.movementRepository = movementRepository;
    }

    public Batch execute(UUID batchId, String reason) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) tenantId = "default";
        String performedBy = "admin-user";

        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new InventoryDomainException("Batch not found: " + batchId));

        int currentQty = batch.getCurrentQuantity();
        batch.releaseFromQuarantine();

        Movement movement = Movement.create(tenantId, batchId, batch.getProductId(),
                MovementType.QUARANTINE_OUT, 0, currentQty, currentQty,
                reason, performedBy);
        movementRepository.save(movement);
        batchRepository.save(batch);

        log.info("Batch released from quarantine: batch={}", batchId);
        return batch;
    }
}
