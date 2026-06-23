package com.medistock.inventory.application.service;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.inventory.domain.model.*;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.inventory.domain.port.MovementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuarantineUseCase")
@SuppressWarnings("java:S5778")
class QuarantineUseCaseTest {

    private static final String TENANT = "test-tenant";

    @Mock private BatchRepository batchRepository;
    @Mock private MovementRepository movementRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Captor private ArgumentCaptor<Movement> movementCaptor;

    private QuarantineUseCase useCase;
    private Batch activeBatch;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        useCase = new QuarantineUseCase(batchRepository, movementRepository, eventPublisher);
        activeBatch = Batch.receive(TENANT, UUID.randomUUID(), "BATCH-001", 100,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.valueOf(15.50));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should quarantine active batch")
    void shouldQuarantineBatch() {
        // Arrange
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = useCase.execute(activeBatch.getId(), "Quality inspection failed");

        // Assert
        assertThat(result.getStatus()).as("status after quarantine").isEqualTo(BatchStatus.QUARANTINED);
        assertThat(result.getCurrentQuantity()).as("current quantity").isEqualTo(100);
        verify(movementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getType()).as("movement type").isEqualTo(MovementType.QUARANTINE_IN);
        verify(eventPublisher).publishEvent(any(BatchQuarantinedEvent.class));
    }

    @Test
    @DisplayName("Should throw when batch not found")
    void shouldThrowWhenBatchNotFound() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(batchRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(id, "reason"))
                .as("Non-existent batch should throw")
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("Batch not found");
    }

    @Test
    @DisplayName("Should throw when batch is already quarantined")
    void shouldThrowWhenAlreadyQuarantined() {
        // Arrange
        activeBatch.quarantine();
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(activeBatch.getId(), "double quarantine"))
                .as("Double quarantine should throw")
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("already QUARANTINED");

        verify(batchRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when batch is disposed")
    void shouldThrowWhenDisposed() {
        // Arrange
        activeBatch.dispose();
        when(batchRepository.findById(activeBatch.getId())).thenReturn(Optional.of(activeBatch));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(activeBatch.getId(), "can't quarantine disposed"))
                .as("Quarantining disposed batch should throw")
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("DISPOSED");
    }
}
