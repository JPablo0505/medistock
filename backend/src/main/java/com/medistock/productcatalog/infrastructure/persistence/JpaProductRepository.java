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
        JpaProduct saved = springRepo.save(jpa);
        return toDomain(saved);
    }

    @Override
    public void delete(Product product) {
        springRepo.deleteById(product.getId());
    }

    private Product toDomain(JpaProduct jpa) {
        Product product = Product.register(jpa.getTenantId(), jpa.getName(), jpa.getUnit());
        // Use reflection-like field update since Product uses a private constructor
        // Instead, we update via the public update() method
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
                jpa.getPurchasePrice()
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
        jpa.setSalePrice(product.getSalePrice() != null ? product.getSalePrice().amount() : java.math.BigDecimal.ZERO);
        jpa.setPurchasePrice(product.getPurchasePrice() != null ? product.getPurchasePrice().amount() : java.math.BigDecimal.ZERO);
        jpa.setActive(product.isActive());
        return jpa;
    }
}
