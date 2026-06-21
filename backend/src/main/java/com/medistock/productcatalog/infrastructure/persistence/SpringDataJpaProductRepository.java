package com.medistock.productcatalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SpringDataJpaProductRepository extends JpaRepository<JpaProduct, UUID> {
    List<JpaProduct> findAllByOrderByName();
    List<JpaProduct> findByTenantIdOrderByName(String tenantId);
    List<JpaProduct> findByTenantIdAndIsActiveOrderByName(String tenantId, boolean isActive);
    long countByTenantId(String tenantId);
    long countByTenantIdAndIsActive(String tenantId, boolean isActive);
    long countByIsActive(boolean isActive);
}
