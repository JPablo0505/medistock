package com.medistock.sales.infrastructure.persistence;

import com.medistock.sales.domain.exception.SaleNotFoundException;
import com.medistock.sales.domain.model.BatchAllocation;
import com.medistock.sales.domain.model.Sale;
import com.medistock.sales.domain.model.SaleItem;
import com.medistock.sales.domain.model.SaleStatus;
import com.medistock.sales.domain.model.SaleType;
import com.medistock.sales.domain.port.SaleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class JpaSaleRepository implements SaleRepository {

    private final SpringDataJpaSaleRepository springRepo;

    public JpaSaleRepository(SpringDataJpaSaleRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Sale> findById(UUID saleId) {
        return springRepo.findById(saleId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Sale> findAll() {
        return springRepo.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Sale save(Sale sale) {
        JpaSaleEntity entity = toEntity(sale);
        JpaSaleEntity saved = springRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Sale> findByTenant(String tenantId, int page, int size, String sort) {
        Sort sorting = parseSort(sort);
        Pageable pageable = PageRequest.of(page, size, sorting);
        return springRepo.findByTenantId(tenantId, pageable)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Sale> findByTenantWithFilters(String tenantId, UUID productId, String status,
                                               String dateFrom, String dateTo,
                                               int page, int size, String sort) {
        Sort sorting = parseSort(sort);
        Pageable pageable = PageRequest.of(page, size, sorting);

        if (productId != null && status != null && !status.isBlank()) {
            return springRepo.findByProductIdAndStatus(tenantId, productId, status, pageable)
                    .stream().map(this::toDomain).toList();
        } else if (productId != null) {
            return springRepo.findByProductId(tenantId, productId, pageable)
                    .stream().map(this::toDomain).toList();
        } else {
            return springRepo.findByTenantIdWithFilters(tenantId,
                            (status != null && !status.isBlank()) ? status : null, pageable)
                    .stream().map(this::toDomain).toList();
        }
    }

    @Override
    public long countByTenant(String tenantId) {
        return springRepo.countByTenantId(tenantId);
    }

    @Override
    public long countByTenantWithFilters(String tenantId, UUID productId, String status,
                                          String dateFrom, String dateTo) {
        // For MVP, simplified counting
        return springRepo.countByTenantId(tenantId);
    }

    // --- Mapping ---

    private JpaSaleEntity toEntity(Sale domain) {
        JpaSaleEntity entity = new JpaSaleEntity();
        entity.setId(domain.getSaleId());
        entity.setTenantId(domain.getTenantId());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setSaleDate(domain.getSaleDate());
        entity.setSaleType(domain.getSaleType().name());
        entity.setSubtotal(domain.getSubtotal());
        entity.setTotalAmount(domain.getTotal());
        entity.setPerformedBy(domain.getPerformedBy());
        entity.setStatus(domain.getStatus().name());
        entity.setVoidedAt(domain.getVoidedAt());
        entity.setVoidReason(domain.getVoidReason());

        // Generate sale number: V-YYYYMMDD-HHMMSS-xxxx (last 4 of UUID)
        if (entity.getSaleNumber() == null) {
            String uuidSuffix = domain.getSaleId().toString().substring(0, 4).toUpperCase();
            entity.setSaleNumber("V-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .format(domain.getSaleDate()) + "-" + uuidSuffix);
        }

        List<JpaSaleItemEntity> itemEntities = domain.getItems().stream()
                .map(item -> toItemEntity(item, entity))
                .collect(Collectors.toList());
        entity.setItems(itemEntities);

        return entity;
    }

    private JpaSaleItemEntity toItemEntity(SaleItem domain, JpaSaleEntity saleEntity) {
        JpaSaleItemEntity entity = new JpaSaleItemEntity();
        entity.setId(domain.getItemId());
        entity.setSale(saleEntity);
        entity.setTenantId(saleEntity.getTenantId());
        entity.setProductId(domain.getProductId());
        entity.setProductName(domain.getProductName());
        entity.setProductSku(domain.getProductSku());
        entity.setQuantity(domain.getQuantity());
        entity.setUnitPrice(domain.getUnitPrice());
        entity.setLineTotal(domain.getTotalPrice());

        List<JpaBatchAllocationEntity> allocEntities = domain.getBatchAllocations().stream()
                .map(alloc -> toAllocationEntity(alloc, entity))
                .collect(Collectors.toList());
        entity.setBatchAllocations(allocEntities);

        return entity;
    }

    private JpaBatchAllocationEntity toAllocationEntity(BatchAllocation domain, JpaSaleItemEntity itemEntity) {
        JpaBatchAllocationEntity entity = new JpaBatchAllocationEntity();
        entity.setId(UUID.randomUUID());
        entity.setItem(itemEntity);
        entity.setBatchId(domain.batchId());
        entity.setBatchCode(domain.lotNumber());
        entity.setExpirationDate(domain.expirationDate());
        entity.setAllocatedQuantity(domain.allocatedQuantity());
        entity.setCostPerUnit(domain.costPerUnit());
        return entity;
    }

    private Sale toDomain(JpaSaleEntity entity) {
        List<SaleItem> items = entity.getItems().stream()
                .map(this::toItemDomain)
                .toList();

        return Sale.restore(
                entity.getId(),
                entity.getTenantId(),
                entity.getSaleDate() != null ? entity.getSaleDate() : entity.getCreatedAt(),
                entity.getSaleType() != null ? SaleType.valueOf(entity.getSaleType()) : SaleType.MANUAL_ENTRY,
                items,
                entity.getSubtotal(),
                entity.getTotalAmount(),
                entity.getPerformedBy(),
                SaleStatus.valueOf(entity.getStatus()),
                entity.getVoidedAt(),
                entity.getVoidReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private SaleItem toItemDomain(JpaSaleItemEntity entity) {
        List<BatchAllocation> allocations = entity.getBatchAllocations().stream()
                .map(this::toAllocationDomain)
                .toList();

        return new SaleItem(
                entity.getProductId(),
                entity.getProductName(),
                entity.getProductSku() != null ? entity.getProductSku() : "",
                entity.getQuantity(),
                entity.getUnitPrice(),
                allocations
        );
    }

    private BatchAllocation toAllocationDomain(JpaBatchAllocationEntity entity) {
        return new BatchAllocation(
                entity.getBatchId(),
                entity.getBatchCode() != null ? entity.getBatchCode() : "",
                entity.getExpirationDate() != null ? entity.getExpirationDate() : LocalDate.now(),
                entity.getAllocatedQuantity(),
                entity.getCostPerUnit() != null ? entity.getCostPerUnit() : BigDecimal.ZERO
        );
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(":");
        String property = switch (parts[0]) {
            case "saleDate" -> "saleDate";
            default -> "createdAt";
        };
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }
}
