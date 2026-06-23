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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VoidSaleUseCase")
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
                LocalDate.of(2027, Month.JUNE, 1), 10, BigDecimal.valueOf(5.00));
        SaleItem item = new SaleItem(PRODUCT_ID, "Ibuprofeno", "IBU-400",
                10, BigDecimal.valueOf(15.50), List.of(alloc));
        completedSale = Sale.create(TENANT_ID, "user-1", List.of(item));
    }

    @Test
    @DisplayName("Should void completed sale and restore stock")
    void shouldVoidCompletedSale() {
        // Arrange
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(completedSale));

        Batch batch = Batch.receive(TENANT_ID, PRODUCT_ID, "LOT-001", 50,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.valueOf(5.00));
        batch.allocate(10);
        when(batchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
        when(saleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        Sale result = useCase.execute(SALE_ID, "Customer return");

        // Assert
        assertEquals(SaleStatus.VOIDED, result.getStatus(), "status");
        assertNotNull(result.getVoidedAt(), "voided timestamp");
        assertEquals("Customer return", result.getVoidReason(), "void reason");

        verify(movementRepository).save(any(Movement.class));
        verify(eventPublisher).publishEvent(any(SaleVoidedEvent.class));
    }

    @Test
    @DisplayName("Should throw when sale is already voided")
    void shouldThrowWhenAlreadyVoided() {
        // Arrange
        completedSale.voidSale("Initial void");
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(completedSale));

        // Act & Assert
        assertThrows(SaleAlreadyVoidedException.class,
                () -> useCase.execute(SALE_ID, "Another void"),
                "Double void should throw SaleAlreadyVoidedException");
        verifyNoInteractions(batchRepository);
    }

    @Test
    @DisplayName("Should throw when sale not found")
    void shouldThrowWhenSaleNotFound() {
        // Arrange
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(SaleNotFoundException.class,
                () -> useCase.execute(SALE_ID, "reason"),
                "Non-existent sale should throw SaleNotFoundException");
    }

    @Test
    @DisplayName("Should throw when void reason is blank")
    void shouldThrowWhenReasonIsBlank() {
        // Act & Assert
        assertThrows(InvalidSaleDataException.class,
                () -> useCase.execute(SALE_ID, ""),
                "Blank reason should throw InvalidSaleDataException");
    }
}
