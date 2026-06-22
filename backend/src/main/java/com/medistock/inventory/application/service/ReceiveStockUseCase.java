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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Use case: Receive a new batch into inventory.
 */
@Service
@Transactional
public class ReceiveStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReceiveStockUseCase.class);

    private final BatchRepository batchRepository;
    private final MovementRepository movementRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ReceiveStockUseCase(BatchRepository batchRepository,
                                MovementRepository movementRepository,
                                ApplicationEventPublisher eventPublisher) {
        this.batchRepository = batchRepository;
        this.movementRepository = movementRepository;
        this.eventPublisher = eventPublisher;
    }

    public Batch execute(UUID productId, String batchCode, int quantity,
                         LocalDate expirationDate, BigDecimal costPerUnit,
                         LocalDate manufacturingDate, String category, String notes) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) tenantId = "default";
        String performedBy = "admin-user";

        Batch batch = Batch.receive(tenantId, productId, batchCode, quantity, expirationDate, costPerUnit);
        batch.setManufacturingDate(manufacturingDate);
        batch.setCategory(category);
        batch.setNotes(notes);

        Batch saved = batchRepository.save(batch);

        Movement movement = Movement.create(tenantId, saved.getId(), productId,
                MovementType.RECEIPT, quantity, 0, quantity,
                "Initial receipt: " + batchCode, performedBy);
        movementRepository.save(movement);

        eventPublisher.publishEvent(new BatchReceivedEvent(
                saved.getId(), productId, quantity, expirationDate, tenantId));

        log.info("Batch received: product={}, batch={}, qty={}", productId, batchCode, quantity);
        return saved;
    }
}
