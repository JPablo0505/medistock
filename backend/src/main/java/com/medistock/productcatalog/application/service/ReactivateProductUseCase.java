package com.medistock.productcatalog.application.service;

import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.model.ProductNotFoundException;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Use case: Reactivate a deactivated product.
 * Loads product, calls reactivate(), saves.
 */
@Service
@Transactional
public class ReactivateProductUseCase {

    private final ProductRepository productRepository;

    public ReactivateProductUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Reactivates a product by its ID.
     * Idempotent — reactivating an already active product does nothing.
     *
     * @return the reactivated product as a DTO
     * @throws ProductNotFoundException if product not found
     */
    public ProductDTO execute(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.reactivate();
        Product saved = productRepository.save(product);
        return RegisterProductUseCase.toDTO(saved);
    }
}
