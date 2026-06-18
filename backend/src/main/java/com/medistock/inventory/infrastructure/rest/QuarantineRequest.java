package com.medistock.inventory.infrastructure.rest;

import jakarta.validation.constraints.NotBlank;

public record QuarantineRequest(
        @NotBlank String reason
) {}
