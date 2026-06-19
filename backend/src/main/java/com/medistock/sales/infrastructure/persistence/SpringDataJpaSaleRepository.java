package com.medistock.sales.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface SpringDataJpaSaleRepository extends JpaRepository<JpaSaleEntity, UUID> {

    @Query("SELECT s FROM JpaSaleEntity s WHERE s.tenantId = :tenantId")
    Page<JpaSaleEntity> findByTenantId(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("""
        SELECT s FROM JpaSaleEntity s
        WHERE s.tenantId = :tenantId
        AND (:status IS NULL OR s.status = :status)
        ORDER BY s.createdAt DESC
        """)
    Page<JpaSaleEntity> findByTenantIdWithFilters(
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            Pageable pageable);

    @Query("""
        SELECT s FROM JpaSaleEntity s
        JOIN s.items i
        WHERE s.tenantId = :tenantId
        AND i.productId = :productId
        ORDER BY s.createdAt DESC
        """)
    Page<JpaSaleEntity> findByProductId(
            @Param("tenantId") String tenantId,
            @Param("productId") UUID productId,
            Pageable pageable);

    @Query("""
        SELECT s FROM JpaSaleEntity s
        JOIN s.items i
        WHERE s.tenantId = :tenantId
        AND i.productId = :productId
        AND (:status IS NULL OR s.status = :status)
        ORDER BY s.createdAt DESC
        """)
    Page<JpaSaleEntity> findByProductIdAndStatus(
            @Param("tenantId") String tenantId,
            @Param("productId") UUID productId,
            @Param("status") String status,
            Pageable pageable);

    long countByTenantId(String tenantId);
}
