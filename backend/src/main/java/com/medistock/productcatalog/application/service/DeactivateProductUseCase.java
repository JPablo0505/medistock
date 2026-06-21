package com.medistock.productcatalog.application.service;

import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.model.ProductNotFoundException;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class DeactivateProductUseCase {

    private final ProductRepository productRepository;

    public DeactivateProductUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductDTO execute(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        if (product.isActive()) {
            product.deactivate();
        }
        Product saved = productRepository.save(product);
        return toDTO(saved);
    }

    private ProductDTO toDTO(Product p) {
        return new ProductDTO(
                p.getId(), p.getTenantId(), p.getName(), p.getGenericName(),
                p.getCategoryId(), p.getConcentration(), p.getDosageForm(),
                p.getManufacturer(), p.isRequiresPrescription(), p.isControlled(),
                p.getStorageCondition(), p.getUnit(), p.getImageUrl(),
                p.getSalePrice() != null ? p.getSalePrice().amount() : null,
                p.getPurchasePrice() != null ? p.getPurchasePrice().amount() : null,
                p.isActive(),
                p.getExpirationDate(), p.getManufacturingDate(),
                0
        );
    }
}
