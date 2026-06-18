package com.medistock.productcatalog.infrastructure.persistence;

import com.medistock.productcatalog.domain.model.Category;
import com.medistock.productcatalog.domain.port.CategoryRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter implementation of CategoryRepository.
 * Maps between JPA entities and domain entities manually (DSG-03).
 */
@Component
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
        return springRepo.findByTenantId(tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Category save(Category category) {
        JpaCategory jpa = toJpa(category);
        JpaCategory saved = springRepo.save(jpa);
        return toDomain(saved);
    }

    // --- Mapping ---

    private Category toDomain(JpaCategory jpa) {
        return Category.reconstitute(
                jpa.getId(),
                jpa.getTenantId(),
                jpa.getName(),
                jpa.getDescription()
        );
    }

    private JpaCategory toJpa(Category domain) {
        JpaCategory jpa = new JpaCategory(
                domain.getName(),
                domain.getDescription()
        );
        jpa.setId(domain.getCategoryId());
        jpa.setTenantId(domain.getTenantId());
        return jpa;
    }
}
