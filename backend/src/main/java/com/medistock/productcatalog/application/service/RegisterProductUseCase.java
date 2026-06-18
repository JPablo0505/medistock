package com.medistock.productcatalog.application.service;

import com.medistock.productcatalog.domain.model.Money;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.model.ProductDomainException;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Use case: Register a new product.
 * Validates input, creates product via domain factory, saves via repository.
 */
@Service
@Transactional
public class RegisterProductUseCase {

    private final ProductRepository productRepository;

    public RegisterProductUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Registers a new product for the given tenant.
     *
     * @return the registered product as a DTO
     * @throws ProductDomainException if validation fails
     */
    public ProductDTO execute(String tenantId, String name, String genericName,
                              UUID categoryId, String concentration, String dosageForm,
                              String manufacturer, boolean requiresPrescription,
                              boolean isControlled, String storageCondition, String unit,
                              BigDecimal salePrice, BigDecimal purchasePrice) {

        Product product = Product.register(
                tenantId, name, genericName, categoryId,
                concentration, dosageForm, manufacturer,
                requiresPrescription, isControlled, storageCondition, unit,
                Money.of(salePrice), Money.of(purchasePrice)
        );

        Product saved = productRepository.save(product);
        return toDTO(saved);
    }

    public static ProductDTO toDTO(Product product) {
        return ProductDTO.builder()
                .id(product.getProductId())
                .tenantId(product.getTenantId())
                .name(product.getName())
                .genericName(product.getGenericName())
                .categoryId(product.getCategoryId())
                .concentration(product.getConcentration())
                .dosageForm(product.getDosageForm())
                .manufacturer(product.getManufacturer())
                .requiresPrescription(product.isRequiresPrescription())
                .isControlled(product.isControlled())
                .storageCondition(product.getStorageCondition())
                .unit(product.getUnit())
                .salePrice(product.getSalePrice().amount())
                .purchasePrice(product.getPurchasePrice().amount())
                .isActive(product.isActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
