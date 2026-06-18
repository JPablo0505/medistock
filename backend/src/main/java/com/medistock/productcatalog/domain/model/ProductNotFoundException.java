package com.medistock.productcatalog.domain.model;

import com.medistock.shared.domain.DomainException;
import java.util.UUID;

/**
 * Thrown when a product is not found by its ID.
 */
public class ProductNotFoundException extends DomainException {

    private static final String ERROR_CODE = "PRODUCT_NOT_FOUND";

    public ProductNotFoundException(UUID productId) {
        super(ERROR_CODE, "Product not found: " + productId);
    }
}
