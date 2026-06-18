package com.medistock.productcatalog.domain.port;

import com.medistock.productcatalog.domain.model.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for the Product aggregate.
 * Defined in the domain layer — implements hexagonal architecture.
 */
public interface ProductRepository {

    Optional<Product> findById(UUID id);

    List<Product> findByTenantId(String tenantId);

    Product save(Product product);

    void delete(Product product);
}
