package com.medistock.sales.application.service;

import com.medistock.inventory.domain.model.Batch;
import com.medistock.inventory.domain.model.Movement;
import com.medistock.inventory.domain.model.MovementType;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.inventory.domain.port.MovementRepository;
import com.medistock.sales.domain.event.SaleVoidedEvent;
import com.medistock.sales.domain.exception.InvalidSaleDataException;
import com.medistock.sales.domain.exception.SaleAlreadyVoidedException;
import com.medistock.sales.domain.exception.SaleNotFoundException;
import com.medistock.sales.domain.model.BatchAllocation;
import com.medistock.sales.domain.model.Sale;
import com.medistock.sales.domain.model.SaleItem;
import com.medistock.sales.domain.model.SaleStatus;
import com.medistock.sales.domain.port.SaleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class VoidSaleUseCase {

    private final SaleRepository saleRepository;
    private final BatchRepository batchRepository;
    private final MovementRepository movementRepository;
    private final ApplicationEventPublisher eventPublisher;

    public VoidSaleUseCase(SaleRepository saleRepository,
                           BatchRepository batchRepository,
                           MovementRepository movementRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.saleRepository = saleRepository;
        this.batchRepository = batchRepository;
        this.movementRepository = movementRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Sale execute(UUID saleId, String reason) {
        if (reason == null || reason.isBlank())
            throw new InvalidSaleDataException("voidReason must not be blank");

        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new SaleNotFoundException(saleId));
        if (sale.getStatus() == SaleStatus.VOIDED)
            throw new SaleAlreadyVoidedException(saleId);

        reverseAllocations(sale);
        sale.voidSale(reason);
        Sale saved = saleRepository.save(sale);

        eventPublisher.publishEvent(new SaleVoidedEvent(
                saved.getSaleId(), saved.getTenantId(), saved.getVoidReason()));
        return saved;
    }

    private void reverseAllocations(Sale sale) {
        for (SaleItem item : sale.getItems()) {
            for (BatchAllocation alloc : item.getBatchAllocations()) {
                restoreBatchAndCreateMovement(sale, item, alloc);
            }
        }
    }

    private void restoreBatchAndCreateMovement(Sale sale, SaleItem item, BatchAllocation alloc) {
        Batch batch = batchRepository.findById(alloc.batchId())
                .orElseThrow(() -> new RuntimeException("Batch not found: " + alloc.batchId()));
        int previousQuantity = batch.getCurrentQuantity();
        batch.restoreQuantity(alloc.allocatedQuantity());
        batchRepository.save(batch);

        movementRepository.save(Movement.create(
                sale.getTenantId(), alloc.batchId(), item.getProductId(),
                MovementType.RETURN, alloc.allocatedQuantity(),
                previousQuantity, batch.getCurrentQuantity(),
                "Void sale " + sale.getSaleId(), sale.getPerformedBy()));
    }
}
