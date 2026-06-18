package com.medistock.productcatalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface SpringDataJpaProductRepository extends JpaRepository<JpaProduct, UUID> {
    List<JpaProduct> findByTenantIdOrderByName(String tenantId);
    List<JpaProduct> findByTenantIdAndIsActiveOrderByName(String tenantId, boolean isActive);
}
