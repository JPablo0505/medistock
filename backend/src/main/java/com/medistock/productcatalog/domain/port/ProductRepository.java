package com.medistock.productcatalog.domain.port;

import com.medistock.productcatalog.domain.model.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    Optional<Product> findById(UUID id);
    List<Product> findAll();
    List<Product> findByTenantId(String tenantId);
    List<Product> findByTenantIdAndIsActive(String tenantId, boolean isActive);
    Product save(Product product);
    void delete(Product product);
}
