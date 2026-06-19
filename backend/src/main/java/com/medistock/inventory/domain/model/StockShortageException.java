package com.medistock.inventory.domain.model;

import com.medistock.shared.domain.DomainException;

/**
 * Thrown when there is insufficient stock to fulfill a requested allocation.
 */
public class StockShortageException extends DomainException {

    private static final String ERROR_CODE = "STOCK_SHORTAGE";

    public StockShortageException(String message) {
        super(ERROR_CODE, message);
    }

    public StockShortageException(int requested, int available) {
        super(ERROR_CODE, "Insufficient stock: requested " + requested + ", available " + available);
    }
}
