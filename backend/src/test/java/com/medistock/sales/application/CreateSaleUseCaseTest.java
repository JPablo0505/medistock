package com.medistock.sales.application;

import com.medistock.inventory.application.service.BatchAllocator;
import com.medistock.inventory.domain.model.AllocationLine;
import com.medistock.inventory.domain.model.AllocationResult;
import com.medistock.inventory.domain.model.StockShortageException;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.port.ProductRepository;
import com.medistock.sales.application.service.CreateSaleCommand;
import com.medistock.sales.application.service.CreateSaleUseCase;
import com.medistock.sales.domain.event.SaleCompletedEvent;
import com.medistock.sales.domain.exception.InvalidSaleDataException;
import com.medistock.sales.domain.exception.ProductNotActiveException;
import com.medistock.sales.domain.model.Sale;
import com.medistock.sales.domain.model.SaleStatus;
import com.medistock.sales.domain.port.SaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateSaleUseCaseTest {

    @Mock private ProductRepository productRepository;
    @Mock private BatchAllocator batchAllocator;
    @Mock private SaleRepository saleRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CreateSaleUseCase useCase;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final String TENANT_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        useCase = new CreateSaleUseCase(productRepository, batchAllocator, saleRepository, eventPublisher);
    }

    // Scenario 1: Success — BatchAllocator called, event published
    @Test
    void shouldCreateSaleSuccessfully() {
        Product product = Product.register(TENANT_ID, "Ibuprofeno 400mg", "tableta");
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        AllocationResult allocResult = AllocationResult.fromLines(PRODUCT_ID, 10,
                List.of(new AllocationLine(BATCH_ID, "LOT-001", 10, BigDecimal.valueOf(5.00),
                        BigDecimal.valueOf(50.00), LocalDate.of(2027, 6, 1))));
        when(batchAllocator.allocate(PRODUCT_ID, 10)).thenReturn(allocResult);

        when(saleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CreateSaleCommand command = new CreateSaleCommand(TENANT_ID, "user-1",
                List.of(new CreateSaleCommand.CreateSaleItem(PRODUCT_ID, "Ibuprofeno 400mg", "IBU-400",
                        10, BigDecimal.valueOf(15.50))));

        Sale result = useCase.execute(command);

        assertNotNull(result);
        assertEquals(SaleStatus.COMPLETED, result.getStatus());
        assertEquals(1, result.getItemCount());

        verify(batchAllocator).allocate(PRODUCT_ID, 10);
        verify(saleRepository).save(any());
        verify(eventPublisher).publishEvent(any(SaleCompletedEvent.class));
    }

    // Scenario 4: InsufficientStock → propagated
    @Test
    void shouldThrowWhenInsufficientStock() {
        Product product = Product.register(TENANT_ID, "Test", "unit");
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(batchAllocator.allocate(PRODUCT_ID, 100))
                .thenThrow(new StockShortageException(100, 50));

        CreateSaleCommand command = new CreateSaleCommand(TENANT_ID, "user-1",
                List.of(new CreateSaleCommand.CreateSaleItem(PRODUCT_ID, "Test", "TST",
                        100, BigDecimal.TEN)));

        assertThrows(StockShortageException.class, () -> useCase.execute(command));
        verify(saleRepository, never()).save(any());
    }

    // Scenario 5: Non-existent product → InvalidSaleDataException
    @Test
    void shouldThrowWhenProductNotFound() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        CreateSaleCommand command = new CreateSaleCommand(TENANT_ID, "user-1",
                List.of(new CreateSaleCommand.CreateSaleItem(PRODUCT_ID, "Test", "TST",
                        10, BigDecimal.TEN)));

        assertThrows(InvalidSaleDataException.class, () -> useCase.execute(command));
        verifyNoInteractions(batchAllocator);
    }

    // Scenario 6: Deactivated product → ProductNotActiveException
    @Test
    void shouldThrowWhenProductDeactivated() {
        Product product = Product.register(TENANT_ID, "Test", "unit");
        product.deactivate();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        CreateSaleCommand command = new CreateSaleCommand(TENANT_ID, "user-1",
                List.of(new CreateSaleCommand.CreateSaleItem(PRODUCT_ID, "Test", "TST",
                        10, BigDecimal.TEN)));

        assertThrows(ProductNotActiveException.class, () -> useCase.execute(command));
    }

    // Scenario 7: Quantity = 0 → InvalidSaleDataException
    @Test
    void shouldThrowWhenQuantityIsZero() {
        CreateSaleCommand command = new CreateSaleCommand(TENANT_ID, "user-1",
                List.of(new CreateSaleCommand.CreateSaleItem(PRODUCT_ID, "Test", "TST",
                        0, BigDecimal.TEN)));

        assertThrows(InvalidSaleDataException.class, () -> useCase.execute(command));
    }

    // Scenario 9: Multi-item — partial failure causes full rollback
    @Test
    void shouldRollbackWhenOneProductFails() {
        UUID productId2 = UUID.randomUUID();
        Product product1 = Product.register(TENANT_ID, "Product A", "unit");
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product1));

        AllocationResult allocOk = AllocationResult.fromLines(PRODUCT_ID, 10,
                List.of(new AllocationLine(BATCH_ID, "LOT-001", 10, BigDecimal.valueOf(5.00),
                        BigDecimal.valueOf(50.00), LocalDate.of(2027, 6, 1))));
        when(batchAllocator.allocate(PRODUCT_ID, 10)).thenReturn(allocOk);

        // Second product not found — triggers rollback
        when(productRepository.findById(productId2)).thenReturn(Optional.empty());

        CreateSaleCommand command = new CreateSaleCommand(TENANT_ID, "user-1",
                List.of(
                        new CreateSaleCommand.CreateSaleItem(PRODUCT_ID, "A", "A", 10, BigDecimal.TEN),
                        new CreateSaleCommand.CreateSaleItem(productId2, "B", "B", 5, BigDecimal.TEN)
                ));

        assertThrows(InvalidSaleDataException.class, () -> useCase.execute(command));
        // BatchAllocator should have been called for product 1 but sale never saved
        verify(batchAllocator).allocate(PRODUCT_ID, 10);
        verify(saleRepository, never()).save(any());
    }
}
