package com.medistock.sales.domain.exception;

import java.util.UUID;

public class SaleAlreadyVoidedException extends RuntimeException {
    public SaleAlreadyVoidedException(UUID saleId) {
        super("Sale is already voided: " + saleId);
    }
}
