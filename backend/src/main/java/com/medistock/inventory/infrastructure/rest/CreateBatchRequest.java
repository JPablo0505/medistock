package com.medistock.inventory.infrastructure.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateBatchRequest(
        @NotNull UUID productId,
        @NotBlank String batchCode,
        @Positive int quantity,
        @NotNull LocalDate expirationDate,
        LocalDate manufacturingDate,
        @NotNull BigDecimal costPerUnit,
        String category,
        String notes
) {}
