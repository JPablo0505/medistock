package com.medistock.sales.domain.model;

import com.medistock.shared.domain.ValueObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BatchAllocation(
        UUID batchId,
        String lotNumber,
        LocalDate expirationDate,
        int allocatedQuantity,
        BigDecimal costPerUnit
) implements ValueObject {

    public BatchAllocation {
        if (batchId == null) throw new IllegalArgumentException("batchId must not be null");
        if (lotNumber == null || lotNumber.isBlank()) throw new IllegalArgumentException("lotNumber must not be blank");
        if (expirationDate == null) throw new IllegalArgumentException("expirationDate must not be null");
        if (allocatedQuantity <= 0) throw new IllegalArgumentException("allocatedQuantity must be > 0");
        if (costPerUnit == null || costPerUnit.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("costPerUnit must not be null or negative");
    }
}
