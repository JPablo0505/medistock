package com.medistock.inventory.infrastructure.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AdjustStockRequest(
        @Positive int newQuantity,
        @NotBlank String reason
) {}
