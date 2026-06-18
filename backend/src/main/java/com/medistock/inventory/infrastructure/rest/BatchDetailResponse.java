package com.medistock.inventory.infrastructure.rest;

import java.util.List;

public record BatchDetailResponse(
        BatchResponse batch,
        List<MovementResponse> movements
) {}
