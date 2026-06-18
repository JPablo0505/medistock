package com.medistock.inventory.domain.model;

import com.medistock.shared.domain.DomainException;

/**
 * Exception thrown when inventory domain invariants are violated.
 */
public class InventoryDomainException extends DomainException {

    public InventoryDomainException(String message) {
        super("INV-001", message);
    }

    public InventoryDomainException(String errorCode, String message) {
        super(errorCode, message);
    }
}
