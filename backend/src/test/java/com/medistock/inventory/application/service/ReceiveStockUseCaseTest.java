package com.medistock.inventory.application.service;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.inventory.domain.model.*;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.inventory.domain.port.MovementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiveStockUseCaseTest {

    private static final String TENANT = "test-tenant";
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock private BatchRepository batchRepository;
    @Mock private MovementRepository movementRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Captor private ArgumentCaptor<Batch> batchCaptor;
    @Captor private ArgumentCaptor<Movement> movementCaptor;

    private ReceiveStockUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        useCase = new ReceiveStockUseCase(batchRepository, movementRepository, eventPublisher);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldReceiveStock() {
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(movementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Batch result = useCase.execute(
                PRODUCT_ID, "BATCH-NEW-001", 200,
                LocalDate.of(2028, Month.JUNE, 15), BigDecimal.valueOf(12.50),
                LocalDate.of(2026, Month.JANUARY, 15), "Tablets", "Initial stock"
        );

        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(result.getBatchCode()).isEqualTo("BATCH-NEW-001");
        assertThat(result.getCurrentQuantity()).isEqualTo(200);
        assertThat(result.getStatus()).isEqualTo(BatchStatus.ACTIVE);
        assertThat(result.getCategory()).isEqualTo("Tablets");
        assertThat(result.getNotes()).isEqualTo("Initial stock");

        verify(batchRepository).save(batchCaptor.capture());
        Batch saved = batchCaptor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(TENANT);

        verify(movementRepository).save(movementCaptor.capture());
        Movement movement = movementCaptor.getValue();
        assertThat(movement.getType()).isEqualTo(MovementType.RECEIPT);
        assertThat(movement.getQuantity()).isEqualTo(200);

        verify(eventPublisher).publishEvent(any(BatchReceivedEvent.class));
    }

    @Test
    void shouldReceiveStockWithoutOptionalFields() {
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(movementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Batch result = useCase.execute(
                PRODUCT_ID, "BATCH-MIN", 50,
                LocalDate.of(2027, Month.DECEMBER, 1), BigDecimal.valueOf(8.00),
                null, null, null
        );

        assertThat(result).isNotNull();
        assertThat(result.getBatchCode()).isEqualTo("BATCH-MIN");
        assertThat(result.getCurrentQuantity()).isEqualTo(50);
        assertThat(result.getCategory()).isNull();
        assertThat(result.getNotes()).isNull();
    }

    @Test
    void shouldRejectNullProductId() {
        assertThatThrownBy(() -> useCase.execute(
                null, "BATCH-001", 100,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.TEN,
                null, null, null))
                .isInstanceOf(InventoryDomainException.class);
    }

    @Test
    void shouldRejectNegativeQuantity() {
        assertThatThrownBy(() -> useCase.execute(
                PRODUCT_ID, "BATCH-001", -1,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.TEN,
                null, null, null))
                .isInstanceOf(InventoryDomainException.class);
    }

    @Test
    void shouldRejectNullExpirationDate() {
        assertThatThrownBy(() -> useCase.execute(
                PRODUCT_ID, "BATCH-001", 100,
                null, BigDecimal.TEN,
                null, null, null))
                .isInstanceOf(InventoryDomainException.class);
    }
}
