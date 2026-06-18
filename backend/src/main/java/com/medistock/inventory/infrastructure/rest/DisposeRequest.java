package com.medistock.inventory.infrastructure.rest;

import jakarta.validation.constraints.NotBlank;

public record DisposeRequest(
        @NotBlank String reason
) {}
