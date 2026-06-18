package com.medistock.productcatalog.infrastructure.persistence;

import com.medistock.productcatalog.domain.model.Category;
import com.medistock.productcatalog.domain.port.CategoryRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter for CategoryRepository.
 * Manual mapping between domain Category and JpaCategory (DSG-03).
 */
@Repository
public class JpaCategoryRepository implements CategoryRepository {

    private final SpringDataJpaCategoryRepository springRepo;

    public JpaCategoryRepository(SpringDataJpaCategoryRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return springRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<Category> findByTenantId(String tenantId) {
        return springRepo.findByTenantIdOrderByName(tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Category save(Category category) {
        JpaCategory jpa = toJpa(category);
        JpaCategory saved = springRepo.save(jpa);
        return toDomain(saved);
    }

    private Category toDomain(JpaCategory jpa) {
        return new Category(jpa.getId(), jpa.getTenantId(), jpa.getName(), jpa.getDescription());
    }

    private JpaCategory toJpa(Category category) {
        JpaCategory jpa = new JpaCategory();
        jpa.setId(category.getId());
        jpa.setTenantId(category.getTenantId());
        jpa.setName(category.getName());
        jpa.setDescription(category.getDescription());
        return jpa;
    }
}
