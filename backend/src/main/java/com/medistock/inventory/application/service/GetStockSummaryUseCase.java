package com.medistock.inventory.application.service;

import com.medistock.inventory.domain.model.*;
import com.medistock.inventory.domain.port.BatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Use case: Compute stock projection summary for a product.
 */
@Service
@Transactional(readOnly = true)
public class GetStockSummaryUseCase {

    private final BatchRepository batchRepository;

    public GetStockSummaryUseCase(BatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

    public StockProjection execute(UUID productId) {
        List<Batch> batches = batchRepository.findByProductId(productId);

        int totalQty = 0;
        int availableQty = 0;
        int quarantinedQty = 0;
        int expiredQty = 0;
        int disposedQty = 0;
        LocalDate nearestExpiry = null;

        for (Batch batch : batches) {
            totalQty += batch.getCurrentQuantity();
            switch (batch.getStatus()) {
                case ACTIVE -> availableQty += batch.getCurrentQuantity();
                case QUARANTINED -> quarantinedQty += batch.getCurrentQuantity();
                case EXPIRED -> expiredQty += batch.getCurrentQuantity();
                case DISPOSED -> disposedQty += batch.getCurrentQuantity();
                case DEPLETED -> {} // no current stock
            }
            if (batch.getStatus() == BatchStatus.ACTIVE && batch.getCurrentQuantity() > 0) {
                if (nearestExpiry == null || batch.getExpirationDate().isBefore(nearestExpiry)) {
                    nearestExpiry = batch.getExpirationDate();
                }
            }
        }

        return new StockProjection(
                productId, totalQty, availableQty, quarantinedQty,
                expiredQty, disposedQty, batches.size(), nearestExpiry);
    }
}
