package com.medistock.productcatalog.domain.model;

import com.medistock.shared.domain.DomainException;

/**
 * Thrown when a product domain rule is violated.
 */
public class ProductDomainException extends DomainException {

    private static final String ERROR_CODE = "PRODUCT_DOMAIN_ERROR";

    public ProductDomainException(String message) {
        super(ERROR_CODE, message);
    }
}
