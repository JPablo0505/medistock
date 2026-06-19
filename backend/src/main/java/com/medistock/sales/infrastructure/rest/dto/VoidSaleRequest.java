package com.medistock.sales.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record VoidSaleRequest(
        @NotBlank String reason
) {}
