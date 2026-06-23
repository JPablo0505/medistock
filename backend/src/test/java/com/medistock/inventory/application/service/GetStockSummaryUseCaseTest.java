package com.medistock.inventory.application.service;

import com.medistock.inventory.domain.model.Batch;
import com.medistock.inventory.domain.model.BatchStatus;
import com.medistock.inventory.domain.port.BatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetStockSummaryUseCase")
class GetStockSummaryUseCaseTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final String TENANT = "tenant-1";
    private static final LocalDate EXPIRY_2027 = LocalDate.of(2027, Month.JUNE, 18);
    private static final LocalDate EXPIRY_2026 = LocalDate.of(2026, Month.DECEMBER, 1);

    @Mock private BatchRepository batchRepository;

    private GetStockSummaryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetStockSummaryUseCase(batchRepository);
    }

    @Test
    @DisplayName("Should compute summary with multiple batches in different states")
    void shouldComputeSummaryWithMultipleBatches() {
        // Arrange
        var active1 = Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 50, EXPIRY_2027, BigDecimal.TEN);
        var active2 = Batch.receive(TENANT, PRODUCT_ID, "BATCH-002", 100, EXPIRY_2026, BigDecimal.valueOf(12));
        var quarantined = Batch.receive(TENANT, PRODUCT_ID, "BATCH-003", 20, EXPIRY_2027, BigDecimal.TEN);
        quarantined.quarantine();

        when(batchRepository.findByProductId(PRODUCT_ID))
                .thenReturn(List.of(active1, active2, quarantined));

        // Act
        var result = useCase.execute(PRODUCT_ID);

        // Assert
        assertThat(result.getTotalQuantity()).as("total quantity").isEqualTo(170);
        assertThat(result.getAvailableQuantity()).as("available quantity").isEqualTo(150);
        assertThat(result.getQuarantinedQuantity()).as("quarantined quantity").isEqualTo(20);
        assertThat(result.getExpiredQuantity()).as("expired quantity").isEqualTo(0);
        assertThat(result.getDisposedQuantity()).as("disposed quantity").isEqualTo(0);
        assertThat(result.getTotalBatches()).as("total batches").isEqualTo(3);
        assertThat(result.getNearestExpiryDate()).as("nearest expiry").isEqualTo(EXPIRY_2026);
    }

    @Test
    @DisplayName("Should include expired and disposed quantities in summary")
    void shouldIncludeExpiredAndDisposedQuantities() {
        // Arrange
        var active = Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 50, EXPIRY_2027, BigDecimal.TEN);
        var expired = Batch.receive(TENANT, PRODUCT_ID, "BATCH-002", 30, EXPIRY_2026, BigDecimal.TEN);
        expired.markExpired();
        var disposed = Batch.receive(TENANT, PRODUCT_ID, "BATCH-003", 10, EXPIRY_2027, BigDecimal.TEN);
        disposed.dispose();

        when(batchRepository.findByProductId(PRODUCT_ID))
                .thenReturn(List.of(active, expired, disposed));

        // Act
        var result = useCase.execute(PRODUCT_ID);

        // Assert
        assertThat(result.getExpiredQuantity()).as("expired quantity").isEqualTo(30);
        assertThat(result.getDisposedQuantity()).as("disposed quantity").isEqualTo(0);
        assertThat(result.getNearestExpiryDate()).as("nearest expiry").isEqualTo(EXPIRY_2027);
    }

    @Test
    @DisplayName("Should handle no batches gracefully")
    void shouldHandleNoBatches() {
        // Arrange
        when(batchRepository.findByProductId(PRODUCT_ID)).thenReturn(List.of());

        // Act
        var result = useCase.execute(PRODUCT_ID);

        // Assert
        assertThat(result.getTotalQuantity()).as("total quantity").isEqualTo(0);
        assertThat(result.getAvailableQuantity()).as("available quantity").isEqualTo(0);
        assertThat(result.getQuarantinedQuantity()).as("quarantined quantity").isEqualTo(0);
        assertThat(result.getTotalBatches()).as("total batches").isEqualTo(0);
        assertThat(result.getNearestExpiryDate()).as("nearest expiry").isNull();
    }

    @Test
    @DisplayName("Should handle depleted batches")
    void shouldHandleDepletedBatches() {
        // Arrange
        var batch = Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100, EXPIRY_2027, BigDecimal.TEN);
        batch.allocate(100);
        when(batchRepository.findByProductId(PRODUCT_ID)).thenReturn(List.of(batch));

        // Act
        var result = useCase.execute(PRODUCT_ID);

        // Assert
        assertThat(result.getTotalQuantity()).as("total quantity").isEqualTo(0);
        assertThat(result.getAvailableQuantity()).as("available quantity").isEqualTo(0);
        assertThat(result.getTotalBatches()).as("total batches").isEqualTo(1);
    }

    @Test
    @DisplayName("Should pick earliest expiry from active batches")
    void shouldPickEarliestExpiryFromActiveBatches() {
        // Arrange
        var later = Batch.receive(TENANT, PRODUCT_ID, "BATCH-LATER", 50,
                LocalDate.of(2028, Month.JUNE, 18), BigDecimal.TEN);
        var earlier = Batch.receive(TENANT, PRODUCT_ID, "BATCH-EARLIER", 100,
                LocalDate.of(2026, Month.JUNE, 18), BigDecimal.TEN);

        when(batchRepository.findByProductId(PRODUCT_ID))
                .thenReturn(List.of(later, earlier));

        // Act
        var result = useCase.execute(PRODUCT_ID);

        // Assert
        assertThat(result.getNearestExpiryDate()).as("nearest expiry").isEqualTo(LocalDate.of(2026, Month.JUNE, 18));
    }

    @Test
    @DisplayName("Should ignore non-active batches for nearest expiry calculation")
    void shouldIgnoreNonActiveBatchesForNearestExpiry() {
        // Arrange
        var active = Batch.receive(TENANT, PRODUCT_ID, "BATCH-ACTIVE", 50,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.TEN);
        var quarantined = Batch.receive(TENANT, PRODUCT_ID, "BATCH-Q", 100,
                LocalDate.of(2026, Month.JUNE, 1), BigDecimal.TEN);
        quarantined.quarantine();

        when(batchRepository.findByProductId(PRODUCT_ID))
                .thenReturn(List.of(active, quarantined));

        // Act
        var result = useCase.execute(PRODUCT_ID);

        // Assert
        assertThat(result.getNearestExpiryDate()).as("nearest expiry").isEqualTo(LocalDate.of(2027, Month.JUNE, 18));
    }
}
