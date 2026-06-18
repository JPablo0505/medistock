package com.medistock.productcatalog.domain.model;

import com.medistock.shared.domain.DomainException;

/**
 * Thrown when product/category domain invariants are violated.
 */
public class ProductDomainException extends DomainException {

    public ProductDomainException(String errorCode, String message) {
        super(errorCode, message);
    }
}
