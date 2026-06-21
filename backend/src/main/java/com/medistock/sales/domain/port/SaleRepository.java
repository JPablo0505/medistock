package com.medistock.sales.domain.port;

import com.medistock.sales.domain.model.Sale;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository {
    Optional<Sale> findById(UUID saleId);
    Sale save(Sale sale);
    List<Sale> findAll();
    List<Sale> findByTenant(String tenantId, int page, int size, String sort);
    List<Sale> findByTenantWithFilters(String tenantId, UUID productId, String status,
                                       String dateFrom, String dateTo,
                                       int page, int size, String sort);
    long countByTenant(String tenantId);
    long countByTenantWithFilters(String tenantId, UUID productId, String status,
                                   String dateFrom, String dateTo);
}
