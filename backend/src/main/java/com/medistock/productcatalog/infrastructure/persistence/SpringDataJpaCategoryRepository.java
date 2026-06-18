package com.medistock.productcatalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface SpringDataJpaCategoryRepository extends JpaRepository<JpaCategory, UUID> {
    List<JpaCategory> findByTenantIdOrderByName(String tenantId);
}
