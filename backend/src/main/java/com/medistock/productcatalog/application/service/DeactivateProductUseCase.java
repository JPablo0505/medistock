package com.medistock.productcatalog.application.service;

import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.model.ProductNotFoundException;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Use case: Soft-deactivate a product.
 * Loads product, calls deactivate(), saves.
 */
@Service
@Transactional
public class DeactivateProductUseCase {

    private final ProductRepository productRepository;

    public DeactivateProductUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Deactivates a product by its ID.
     * Idempotent — deactivating an already inactive product does nothing.
     *
     * @return the deactivated product as a DTO
     * @throws ProductNotFoundException if product not found
     */
    public ProductDTO execute(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.deactivate();
        Product saved = productRepository.save(product);
        return RegisterProductUseCase.toDTO(saved);
    }
}
