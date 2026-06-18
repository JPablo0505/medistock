package com.medistock.inventory.infrastructure.persistence;

import com.medistock.inventory.domain.model.Batch;
import com.medistock.inventory.domain.model.BatchStatus;
import com.medistock.inventory.domain.port.BatchRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter for BatchRepository.
 * Manual mapping between domain Batch and JpaBatch (DSG-03).
 */
@Repository
public class JpaBatchRepository implements BatchRepository {

    private final SpringDataJpaBatchRepository springRepo;

    public JpaBatchRepository(SpringDataJpaBatchRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Batch save(Batch batch) {
        JpaBatch jpa = toJpa(batch);
        JpaBatch saved = springRepo.save(jpa);
        return toDomain(saved);
    }

    @Override
    public Optional<Batch> findById(UUID id) {
        return springRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<Batch> findByProductId(UUID productId) {
        return springRepo.findByProductIdOrderByExpirationDateAsc(productId.toString()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Batch> findAvailableByProductOrderedByExpiry(UUID productId) {
        return springRepo.findAvailableFefo(productId.toString()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Batch> findAllByTenantId(String tenantId) {
        return springRepo.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void delete(Batch batch) {
        springRepo.deleteById(batch.getId());
    }

    // --- Mapping ---

    private Batch toDomain(JpaBatch jpa) {
        return Batch.restore(
                jpa.getId(),
                jpa.getTenantId(),
                UUID.fromString(jpa.getProductId()),
                jpa.getBatchCode(),
                jpa.getInitialQuantity(),
                jpa.getCurrentQuantity(),
                jpa.getExpirationDate(),
                jpa.getManufacturingDate(),
                jpa.getCostPerUnit(),
                BatchStatus.valueOf(jpa.getStatus()),
                jpa.getCategory(),
                jpa.getNotes()
        );
    }

    private JpaBatch toJpa(Batch batch) {
        JpaBatch jpa = new JpaBatch();
        jpa.setId(batch.getId());
        jpa.setTenantId(batch.getTenantId());
        jpa.setProductId(batch.getProductId().toString());
        jpa.setBatchCode(batch.getBatchCode());
        jpa.setInitialQuantity(batch.getInitialQuantity());
        jpa.setCurrentQuantity(batch.getCurrentQuantity());
        jpa.setExpirationDate(batch.getExpirationDate());
        jpa.setManufacturingDate(batch.getManufacturingDate());
        jpa.setCostPerUnit(batch.getCostPerUnit());
        jpa.setStatus(batch.getStatus().name());
        jpa.setCategory(batch.getCategory());
        jpa.setNotes(batch.getNotes());
        return jpa;
    }
}
