package com.medistock.sales.application;

import com.medistock.inventory.domain.model.Batch;
import com.medistock.inventory.domain.model.Movement;
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
import com.medistock.sales.application.service.VoidSaleUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class VoidSaleUseCaseTest {

    @Mock private SaleRepository saleRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private MovementRepository movementRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private VoidSaleUseCase useCase;

    private static final UUID SALE_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final String TENANT_ID = "tenant-1";

    private Sale completedSale;

    @BeforeEach
    void setUp() {
        useCase = new VoidSaleUseCase(saleRepository, batchRepository, movementRepository, eventPublisher);

        BatchAllocation alloc = new BatchAllocation(BATCH_ID, "LOT-001",
                LocalDate.of(2027, 6, 1), 10, BigDecimal.valueOf(5.00));
        SaleItem item = new SaleItem(PRODUCT_ID, "Ibuprofeno", "IBU-400",
                10, BigDecimal.valueOf(15.50), List.of(alloc));
        completedSale = Sale.create(TENANT_ID, "user-1", List.of(item));
    }

    // Scenario 10: Void completed sale — stock restored
    @Test
    void shouldVoidCompletedSale() {
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(completedSale));

        Batch batch = Batch.receive(TENANT_ID, PRODUCT_ID, "LOT-001", 50,
                LocalDate.of(2027, 6, 18), BigDecimal.valueOf(5.00));
        batch.allocate(10); // simulate 10 already allocated
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
        when(saleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Sale result = useCase.execute(SALE_ID, "Customer return");

        assertEquals(SaleStatus.VOIDED, result.getStatus());
        assertNotNull(result.getVoidedAt());
        assertEquals("Customer return", result.getVoidReason());

        verify(movementRepository).save(any(Movement.class));
        verify(eventPublisher).publishEvent(any(SaleVoidedEvent.class));
    }

    // Scenario 11: Already voided → SaleAlreadyVoidedException
    @Test
    void shouldThrowWhenAlreadyVoided() {
        completedSale.voidSale("Initial void");
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(completedSale));

        assertThrows(SaleAlreadyVoidedException.class,
                () -> useCase.execute(SALE_ID, "Another void"));
        verifyNoInteractions(batchRepository);
    }

    // Not found → SaleNotFoundException
    @Test
    void shouldThrowWhenSaleNotFound() {
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.empty());
        assertThrows(SaleNotFoundException.class,
                () -> useCase.execute(SALE_ID, "reason"));
    }

    // Blank reason → InvalidSaleDataException
    @Test
    void shouldThrowWhenReasonIsBlank() {
        assertThrows(InvalidSaleDataException.class,
                () -> useCase.execute(SALE_ID, ""));
    }
}
