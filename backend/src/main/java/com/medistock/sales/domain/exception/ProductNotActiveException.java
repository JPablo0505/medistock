package com.medistock.sales.domain.exception;

import java.util.UUID;

public class ProductNotActiveException extends RuntimeException {
    public ProductNotActiveException(UUID productId) {
        super("Product is not active: " + productId);
    }
}
