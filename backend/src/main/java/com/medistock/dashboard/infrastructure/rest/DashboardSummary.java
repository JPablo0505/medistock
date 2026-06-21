package com.medistock.dashboard.infrastructure.rest;

public record DashboardSummary(
        long totalProducts,
        long activeProducts,
        long activeBatches,
        long activeAlerts,
        long expiringSoonBatches
) {}
