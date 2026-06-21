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
 * Use case: Write off (dispose) a batch.
 */
@Service
@Transactional
public class WriteOffUseCase {

    private static final Logger log = LoggerFactory.getLogger(WriteOffUseCase.class);

    private final BatchRepository batchRepository;
    private final MovementRepository movementRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WriteOffUseCase(BatchRepository batchRepository,
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
        batch.dispose();

        Movement movement = Movement.record(tenantId, batchId, batch.getProductId(),
                MovementType.WRITE_OFF, -previousQuantity, previousQuantity, 0,
                reason, performedBy);
        movementRepository.save(movement);
        batchRepository.save(batch);

        eventPublisher.publishEvent(new BatchDisposedEvent(
                batchId, batch.getProductId(), reason, previousQuantity, tenantId));

        log.info("Batch disposed: batch={}, qty={}, reason={}", batchId, previousQuantity, reason);
        return batch;
    }
}
