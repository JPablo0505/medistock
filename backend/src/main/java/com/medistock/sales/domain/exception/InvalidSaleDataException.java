package com.medistock.sales.domain.exception;

public class InvalidSaleDataException extends RuntimeException {
    public InvalidSaleDataException(String message) {
        super(message);
    }
}
