package com.medistock.productcatalog.infrastructure.persistence;

import com.medistock.productcatalog.domain.model.Money;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter for ProductRepository.
 * Manual mapping between domain Product and JpaProduct (DSG-03).
 */
@Repository
public class JpaProductRepository implements ProductRepository {

    private final SpringDataJpaProductRepository springRepo;

    public JpaProductRepository(SpringDataJpaProductRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return springRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<Product> findAll() {
        return springRepo.findAllByOrderByName().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Product> findByTenantId(String tenantId) {
        return springRepo.findByTenantIdOrderByName(tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Product> findByTenantIdAndIsActive(String tenantId, boolean isActive) {
        return springRepo.findByTenantIdAndIsActiveOrderByName(tenantId, isActive).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Product save(Product product) {
        JpaProduct jpa = toJpa(product);
        // Preserve version to prevent isNew()=true which causes persist() instead of merge()
        springRepo.findById(product.getId()).ifPresent(existing ->
                jpa.setVersion(existing.getVersion()));
        JpaProduct saved = springRepo.save(jpa);
        return toDomain(saved);
    }

    @Override
    public void delete(Product product) {
        springRepo.deleteById(product.getId());
    }

    private Product toDomain(JpaProduct jpa) {
        Product product = Product.reconstitute(
                jpa.getId(), jpa.getTenantId(), jpa.getName(), jpa.getUnit(), jpa.getImageUrl(),
                jpa.getExpirationDate(), jpa.getManufacturingDate()
        );
        product.update(
                jpa.getName(),
                jpa.getGenericName(),
                jpa.getCategoryId(),
                jpa.getConcentration(),
                jpa.getDosageForm(),
                jpa.getManufacturer(),
                jpa.isRequiresPrescription(),
                jpa.isControlled(),
                jpa.getStorageCondition(),
                jpa.getUnit(),
                jpa.getSalePrice(),
                jpa.getPurchasePrice(),
                jpa.getImageUrl(),
                jpa.getExpirationDate(),
                jpa.getManufacturingDate()
        );
        if (!jpa.isActive()) {
            try { product.deactivate(); } catch (Exception e) { /* already inactive */ }
        }
        return product;
    }

    private JpaProduct toJpa(Product product) {
        JpaProduct jpa = new JpaProduct();
        jpa.setId(product.getId());
        jpa.setTenantId(product.getTenantId());
        jpa.setName(product.getName());
        jpa.setGenericName(product.getGenericName());
        jpa.setCategoryId(product.getCategoryId());
        jpa.setConcentration(product.getConcentration());
        jpa.setDosageForm(product.getDosageForm());
        jpa.setManufacturer(product.getManufacturer());
        jpa.setRequiresPrescription(product.isRequiresPrescription());
        jpa.setControlled(product.isControlled());
        jpa.setStorageCondition(product.getStorageCondition());
        jpa.setUnit(product.getUnit());
        jpa.setImageUrl(product.getImageUrl());
        jpa.setSalePrice(product.getSalePrice() != null ? product.getSalePrice().amount() : java.math.BigDecimal.ZERO);
        jpa.setPurchasePrice(product.getPurchasePrice() != null ? product.getPurchasePrice().amount() : java.math.BigDecimal.ZERO);
        jpa.setActive(product.isActive());
        jpa.setExpirationDate(product.getExpirationDate());
        jpa.setManufacturingDate(product.getManufacturingDate());
        return jpa;
    }
}
