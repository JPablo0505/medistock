package com.medistock.productcatalog.application.service;

import com.medistock.productcatalog.domain.model.Money;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.model.ProductNotFoundException;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Use case: Update an existing product.
 * Loads product, applies updates, saves.
 */
@Service
@Transactional
public class UpdateProductUseCase {

    private final ProductRepository productRepository;

    public UpdateProductUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Updates an existing product.
     *
     * @return the updated product as a DTO
     * @throws ProductNotFoundException if product not found
     */
    public ProductDTO execute(UUID productId, String name, String genericName,
                              UUID categoryId, String concentration, String dosageForm,
                              String manufacturer, boolean requiresPrescription,
                              boolean isControlled, String storageCondition, String unit,
                              BigDecimal salePrice, BigDecimal purchasePrice) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.update(name, genericName, categoryId,
                concentration, dosageForm, manufacturer,
                requiresPrescription, isControlled, storageCondition, unit,
                Money.of(salePrice), Money.of(purchasePrice));

        Product saved = productRepository.save(product);
        return RegisterProductUseCase.toDTO(saved);
    }
}
