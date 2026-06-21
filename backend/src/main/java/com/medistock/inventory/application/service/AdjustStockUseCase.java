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
 * Use case: Adjust the stock level of an existing batch.
 */
@Service
@Transactional
public class AdjustStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdjustStockUseCase.class);

    private final BatchRepository batchRepository;
    private final MovementRepository movementRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdjustStockUseCase(BatchRepository batchRepository,
                               MovementRepository movementRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.batchRepository = batchRepository;
        this.movementRepository = movementRepository;
        this.eventPublisher = eventPublisher;
    }

    public Batch execute(UUID batchId, int newQuantity, String reason) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) tenantId = "default";
        String performedBy = "admin-user";

        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new InventoryDomainException("Batch not found: " + batchId));

        int previousQuantity = batch.getCurrentQuantity();
        int diff = newQuantity - previousQuantity;

        batch.adjustQuantity(newQuantity, reason);

        MovementType type = diff >= 0 ? MovementType.ADJUSTMENT : MovementType.ADJUSTMENT;
        Movement movement = Movement.record(tenantId, batchId, batch.getProductId(),
                type, diff, previousQuantity, newQuantity, reason, performedBy);
        movementRepository.save(movement);

        batchRepository.save(batch);

        eventPublisher.publishEvent(new StockAdjustedEvent(
                batchId, batch.getProductId(), previousQuantity, newQuantity, reason, tenantId));

        log.info("Stock adjusted: batch={}, {} -> {}, reason={}", batchId, previousQuantity, newQuantity, reason);
        return batch;
    }
}
