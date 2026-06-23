package com.medistock.sales.application.service;

import com.medistock.inventory.application.service.BatchAllocator;
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

import java.time.Clock;
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

        List<SaleItem> saleItems = command.items().stream()
                .map(this::createSaleItem)
                .toList();

        // Build and persist sale aggregate
        Sale sale = Sale.create(command.tenantId(), command.performedBy(), saleItems);
        Sale saved = saleRepository.save(sale);

        // Publish event (within same @Transactional — AFTER_COMMIT listeners see it)
        eventPublisher.publishEvent(new SaleCompletedEvent(
                saved.getSaleId(), saved.getTenantId(), saved.getTotal(), saved.getPerformedBy()));

        return saved;
    }

    private SaleItem createSaleItem(CreateSaleCommand.CreateSaleItem item) {
        validateItem(item);
        Product product = productRepository.findById(item.productId())
                .orElseThrow(() -> new InvalidSaleDataException("Product not found: " + item.productId()));
        if (!product.isActive()) throw new ProductNotActiveException(item.productId());

        AllocationResult allocation = batchAllocator.allocate(item.productId(), item.quantity());
        List<BatchAllocation> batchAllocations = mapAllocations(allocation);

        return new SaleItem(
                item.productId(),
                item.productName() != null ? item.productName() : product.getName(),
                item.productSku() != null ? item.productSku() : "",
                item.quantity(), item.unitPrice(), batchAllocations);
    }

    private void validateItem(CreateSaleCommand.CreateSaleItem item) {
        if (item.quantity() <= 0)
            throw new InvalidSaleDataException("Item quantity must be > 0 for product " + item.productId());
    }

    private List<BatchAllocation> mapAllocations(AllocationResult allocation) {
        return allocation.lines().stream()
                .map(line -> new BatchAllocation(
                        line.batchId(),
                        line.batchCode() != null ? line.batchCode() : "",
                        line.expirationDate() != null ? line.expirationDate() : java.time.LocalDate.now(Clock.systemDefaultZone()),
                        line.quantityAllocated(),
                        line.unitCost() != null ? line.unitCost() : java.math.BigDecimal.ZERO))
                .toList();
    }
}
