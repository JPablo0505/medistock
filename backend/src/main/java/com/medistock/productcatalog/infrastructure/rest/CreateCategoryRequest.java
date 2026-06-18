package com.medistock.productcatalog.infrastructure.rest;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
        @NotBlank String name,
        String description
) {}
