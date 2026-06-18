package com.medistock.productcatalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for JpaProduct.
 * Internal to infrastructure — not exposed to domain.
 */
@Repository
interface SpringDataJpaProductRepository extends JpaRepository<JpaProduct, UUID> {

    List<JpaProduct> findByTenantId(String tenantId);
}
