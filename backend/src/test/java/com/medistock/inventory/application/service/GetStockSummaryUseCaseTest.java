package com.medistock.inventory.application.service;

import com.medistock.inventory.domain.model.Batch;
import com.medistock.inventory.domain.model.BatchStatus;
import com.medistock.inventory.domain.port.BatchRepository;
import org.junit.jupiter.api.BeforeEach;
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
    void shouldComputeSummaryWithMultipleBatches() {
        var active1 = Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 50, EXPIRY_2027, BigDecimal.TEN);
        var active2 = Batch.receive(TENANT, PRODUCT_ID, "BATCH-002", 100, EXPIRY_2026, BigDecimal.valueOf(12));
        var quarantined = Batch.receive(TENANT, PRODUCT_ID, "BATCH-003", 20, EXPIRY_2027, BigDecimal.TEN);
        quarantined.quarantine();

        when(batchRepository.findByProductId(PRODUCT_ID))
                .thenReturn(List.of(active1, active2, quarantined));

        var result = useCase.execute(PRODUCT_ID);

        assertThat(result.getTotalQuantity()).isEqualTo(170);
        assertThat(result.getAvailableQuantity()).isEqualTo(150);
        assertThat(result.getQuarantinedQuantity()).isEqualTo(20);
        assertThat(result.getExpiredQuantity()).isEqualTo(0);
        assertThat(result.getDisposedQuantity()).isEqualTo(0);
        assertThat(result.getTotalBatches()).isEqualTo(3);
        assertThat(result.getNearestExpiryDate()).isEqualTo(EXPIRY_2026);
    }

    @Test
    void shouldIncludeExpiredAndDisposedQuantities() {
        var active = Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 50, EXPIRY_2027, BigDecimal.TEN);
        var expired = Batch.receive(TENANT, PRODUCT_ID, "BATCH-002", 30, EXPIRY_2026, BigDecimal.TEN);
        expired.markExpired();
        var disposed = Batch.receive(TENANT, PRODUCT_ID, "BATCH-003", 10, EXPIRY_2027, BigDecimal.TEN);
        disposed.dispose();

        when(batchRepository.findByProductId(PRODUCT_ID))
                .thenReturn(List.of(active, expired, disposed));

        var result = useCase.execute(PRODUCT_ID);

        assertThat(result.getExpiredQuantity()).isEqualTo(30);
        assertThat(result.getDisposedQuantity()).isEqualTo(0); // dispose sets qty to 0
        assertThat(result.getNearestExpiryDate()).isEqualTo(EXPIRY_2027);
    }

    @Test
    void shouldHandleNoBatches() {
        when(batchRepository.findByProductId(PRODUCT_ID)).thenReturn(List.of());

        var result = useCase.execute(PRODUCT_ID);

        assertThat(result.getTotalQuantity()).isEqualTo(0);
        assertThat(result.getAvailableQuantity()).isEqualTo(0);
        assertThat(result.getQuarantinedQuantity()).isEqualTo(0);
        assertThat(result.getTotalBatches()).isEqualTo(0);
        assertThat(result.getNearestExpiryDate()).isNull();
    }

    @Test
    void shouldHandleDepletedBatches() {
        var batch = Batch.receive(TENANT, PRODUCT_ID, "BATCH-001", 100, EXPIRY_2027, BigDecimal.TEN);
        batch.allocate(100); // depletes it

        when(batchRepository.findByProductId(PRODUCT_ID)).thenReturn(List.of(batch));

        var result = useCase.execute(PRODUCT_ID);

        assertThat(result.getTotalQuantity()).isEqualTo(0);
        assertThat(result.getAvailableQuantity()).isEqualTo(0);
        assertThat(result.getTotalBatches()).isEqualTo(1);
    }

    @Test
    void shouldPickEarliestExpiryFromActiveBatches() {
        var later = Batch.receive(TENANT, PRODUCT_ID, "BATCH-LATER", 50,
                LocalDate.of(2028, Month.JUNE, 18), BigDecimal.TEN);
        var earlier = Batch.receive(TENANT, PRODUCT_ID, "BATCH-EARLIER", 100,
                LocalDate.of(2026, Month.JUNE, 18), BigDecimal.TEN);

        when(batchRepository.findByProductId(PRODUCT_ID))
                .thenReturn(List.of(later, earlier));

        var result = useCase.execute(PRODUCT_ID);

        assertThat(result.getNearestExpiryDate()).isEqualTo(LocalDate.of(2026, Month.JUNE, 18));
    }

    @Test
    void shouldIgnoreNonActiveBatchesForNearestExpiry() {
        var active = Batch.receive(TENANT, PRODUCT_ID, "BATCH-ACTIVE", 50,
                LocalDate.of(2027, Month.JUNE, 18), BigDecimal.TEN);
        var quarantined = Batch.receive(TENANT, PRODUCT_ID, "BATCH-Q", 100,
                LocalDate.of(2026, Month.JUNE, 1), BigDecimal.TEN);
        quarantined.quarantine();

        when(batchRepository.findByProductId(PRODUCT_ID))
                .thenReturn(List.of(active, quarantined));

        var result = useCase.execute(PRODUCT_ID);

        // Nearest expiry should be from active batch, not quarantined
        assertThat(result.getNearestExpiryDate()).isEqualTo(LocalDate.of(2027, Month.JUNE, 18));
    }
}
