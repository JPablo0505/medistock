package com.medistock.inventory.application.service;

import com.medistock.inventory.domain.model.*;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.inventory.domain.port.MovementRepository;
import com.medistock.crosscutting.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use case: Place a batch under quarantine.
 */
@Service
@Transactional
public class QuarantineUseCase {

    private static final Logger log = LoggerFactory.getLogger(QuarantineUseCase.class);

    private final BatchRepository batchRepository;
    private final MovementRepository movementRepository;
    private final ApplicationEventPublisher eventPublisher;

    public QuarantineUseCase(BatchRepository batchRepository,
                              MovementRepository movementRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.batchRepository = batchRepository;
        this.movementRepository = movementRepository;
        this.eventPublisher = eventPublisher;
    }

    public Batch execute(UUID batchId, String reason) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) tenantId = "default";
        String performedBy = "admin-user";

        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new InventoryDomainException("Batch not found: " + batchId));

        int previousQuantity = batch.getCurrentQuantity();
        batch.quarantine();

        Movement movement = Movement.record(tenantId, batchId, batch.getProductId(),
                MovementType.QUARANTINE_IN, 0, previousQuantity, previousQuantity,
                reason, performedBy);
        movementRepository.save(movement);
        batchRepository.save(batch);

        eventPublisher.publishEvent(new BatchQuarantinedEvent(
                batchId, batch.getProductId(), reason, tenantId));

        log.info("Batch quarantined: batch={}, reason={}", batchId, reason);
        return batch;
    }
}
