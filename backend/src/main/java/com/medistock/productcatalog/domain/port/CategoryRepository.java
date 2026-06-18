package com.medistock.productcatalog.domain.port;

import com.medistock.productcatalog.domain.model.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {
    Optional<Category> findById(UUID id);
    List<Category> findByTenantId(String tenantId);
    Category save(Category category);
}
