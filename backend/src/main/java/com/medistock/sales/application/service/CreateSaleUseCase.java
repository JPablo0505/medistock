package com.medistock.sales.application.service;

import com.medistock.inventory.application.service.BatchAllocator;
import com.medistock.inventory.domain.model.AllocationLine;
import com.medistock.inventory.domain.model.AllocationResult;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.port.ProductRepository;
import com.medistock.sales.domain.event.SaleCompletedEvent;
import com.medistock.sales.domain.exception.InvalidSaleDataException;
import com.medistock.sales.domain.exception.ProductNotActiveException;
import com.medistock.sales.domain.model.BatchAllocation;
import com.medistock.sales.domain.model.Sale;
import com.medistock.sales.domain.model.SaleItem;
import com.medistock.sales.domain.port.SaleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CreateSaleUseCase {

    private final ProductRepository productRepository;
    private final BatchAllocator batchAllocator;
    private final SaleRepository saleRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CreateSaleUseCase(ProductRepository productRepository,
                             BatchAllocator batchAllocator,
                             SaleRepository saleRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.batchAllocator = batchAllocator;
        this.saleRepository = saleRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Sale execute(CreateSaleCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new InvalidSaleDataException("Sale must have at least one item");
        }

        List<SaleItem> saleItems = new ArrayList<>();

        for (CreateSaleCommand.CreateSaleItem item : command.items()) {
            if (item.quantity() <= 0) {
                throw new InvalidSaleDataException("Item quantity must be > 0 for product " + item.productId());
            }

            // Validate product exists and is active
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new InvalidSaleDataException(
                            "Product not found: " + item.productId()));
            if (!product.isActive()) {
                throw new ProductNotActiveException(item.productId());
            }

            // SAL-R04: FEFO allocation via BatchAllocator (one call per product)
            AllocationResult allocation = batchAllocator.allocate(
                    item.productId(), item.quantity());

            // Map AllocationLine to domain BatchAllocation
            List<BatchAllocation> batchAllocations = allocation.lines().stream()
                    .map(line -> new BatchAllocation(
                            line.batchId(),
                            line.batchCode() != null ? line.batchCode() : "",
                            line.expirationDate() != null ? line.expirationDate() : java.time.LocalDate.now(),
                            line.quantityAllocated(),
                            line.unitCost() != null ? line.unitCost() : java.math.BigDecimal.ZERO
                    ))
                    .toList();

            // Snapshot product info at time of sale (SAL-R06, SAL-R12)
            SaleItem saleItem = new SaleItem(
                    item.productId(),
                    item.productName() != null ? item.productName() : product.getName(),
                    item.productSku() != null ? item.productSku() : "",
                    item.quantity(),
                    item.unitPrice(),
                    batchAllocations
            );
            saleItems.add(saleItem);
        }

        // Build and persist sale aggregate
        Sale sale = Sale.create(command.tenantId(), command.performedBy(), saleItems);
        Sale saved = saleRepository.save(sale);

        // Publish event (within same @Transactional — AFTER_COMMIT listeners see it)
        eventPublisher.publishEvent(new SaleCompletedEvent(
                saved.getSaleId(), saved.getTenantId(), saved.getTotal(), saved.getPerformedBy()));

        return saved;
    }
}
