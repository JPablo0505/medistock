package com.medistock.productcatalog.infrastructure.persistence;

import com.medistock.productcatalog.domain.model.Money;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter implementation of ProductRepository.
 * Maps between JPA entities and domain entities manually (DSG-03).
 */
@Component
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
    public List<Product> findByTenantId(String tenantId) {
        return springRepo.findByTenantId(tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Product save(Product product) {
        JpaProduct jpa = toJpa(product);
        JpaProduct saved = springRepo.save(jpa);
        return toDomain(saved);
    }

    @Override
    public void delete(Product product) {
        springRepo.findById(product.getProductId())
                .ifPresent(springRepo::delete);
    }

    // --- Mapping ---

    private Product toDomain(JpaProduct jpa) {
        return Product.reconstitute(
                jpa.getId(),
                jpa.getTenantId(),
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
                Money.of(jpa.getSalePrice()),
                Money.of(jpa.getPurchasePrice()),
                jpa.isActive(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt()
        );
    }

    private JpaProduct toJpa(Product domain) {
        JpaProduct jpa = new JpaProduct(
                domain.getName(),
                domain.getGenericName(),
                domain.getCategoryId(),
                domain.getConcentration(),
                domain.getDosageForm(),
                domain.getManufacturer(),
                domain.isRequiresPrescription(),
                domain.isControlled(),
                domain.getStorageCondition(),
                domain.getUnit(),
                domain.getSalePrice().amount(),
                domain.getPurchasePrice().amount(),
                domain.isActive()
        );
        jpa.setId(domain.getProductId());
        jpa.setTenantId(domain.getTenantId());
        return jpa;
    }
}
