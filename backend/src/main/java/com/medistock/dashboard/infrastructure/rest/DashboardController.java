package com.medistock.dashboard.infrastructure.rest;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.productcatalog.infrastructure.persistence.SpringDataJpaProductRepository;
import com.medistock.inventory.infrastructure.persistence.SpringDataJpaBatchRepository;
import com.medistock.alerts.infrastructure.persistence.SpringDataJpaAlertInstanceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final SpringDataJpaProductRepository productRepo;
    private final SpringDataJpaBatchRepository batchRepo;
    private final SpringDataJpaAlertInstanceRepository alertRepo;

    public DashboardController(SpringDataJpaProductRepository productRepo,
                               SpringDataJpaBatchRepository batchRepo,
                               SpringDataJpaAlertInstanceRepository alertRepo) {
        this.productRepo = productRepo;
        this.batchRepo = batchRepo;
        this.alertRepo = alertRepo;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummary> summary() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) tenantId = "default";

        long totalProducts = productRepo.countByTenantId(tenantId);
        long activeProducts = productRepo.countByTenantIdAndIsActive(tenantId, true);
        long activeBatches = batchRepo.countByTenantIdAndStatus(tenantId, STATUS_ACTIVE);
        long activeAlerts = alertRepo.countByTenantIdAndStatus(tenantId, STATUS_ACTIVE);

        LocalDate today = LocalDate.now(Clock.systemDefaultZone());
        LocalDate in30Days = today.plusDays(30);
        long expiringSoonBatches = batchRepo.countByTenantIdAndStatusAndExpirationDateBetween(
                tenantId, STATUS_ACTIVE, today, in30Days);

        DashboardSummary summary = new DashboardSummary(
                totalProducts, activeProducts, activeBatches, activeAlerts, expiringSoonBatches
        );
        return ResponseEntity.ok(summary);
    }
}
