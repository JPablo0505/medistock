package com.medistock.productcatalog.domain.model;

import com.medistock.shared.domain.ValueObject;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representing a monetary amount.
 * Immutable, equality-based, with currency support.
 */
public record Money(BigDecimal amount, String currency) implements ValueObject {

    private static final String DEFAULT_CURRENCY = "COP";

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount, DEFAULT_CURRENCY);
    }

    public static Money of(double amount) {
        return of(BigDecimal.valueOf(amount));
    }
}
