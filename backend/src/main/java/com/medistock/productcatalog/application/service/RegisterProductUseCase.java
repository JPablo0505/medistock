package com.medistock.productcatalog.application.service;

import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.port.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterProductUseCase {

    private final ProductRepository productRepository;

    public RegisterProductUseCase(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductDTO execute(String tenantId, String name, String unit) {
        Product product = Product.register(tenantId, name, unit);
        Product saved = productRepository.save(product);
        return toDTO(saved);
    }

    private ProductDTO toDTO(Product p) {
        return new ProductDTO(
                p.getId(), p.getTenantId(), p.getName(), p.getGenericName(),
                p.getCategoryId(), p.getConcentration(), p.getDosageForm(),
                p.getManufacturer(), p.isRequiresPrescription(), p.isControlled(),
                p.getStorageCondition(), p.getUnit(),
                p.getSalePrice() != null ? p.getSalePrice().amount() : null,
                p.getPurchasePrice() != null ? p.getPurchasePrice().amount() : null,
                p.isActive()
        );
    }
}
