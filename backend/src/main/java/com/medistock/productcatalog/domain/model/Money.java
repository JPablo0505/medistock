package com.medistock.productcatalog.domain.model;

import com.medistock.shared.domain.ValueObject;
import java.math.BigDecimal;

/**
 * Value object representing a monetary amount with currency.
 * Immutable, equality-based, and validates that amount is non-negative.
 */
public record Money(BigDecimal amount, String currency) implements ValueObject {

    private static final String DEFAULT_CURRENCY = "COP";

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency must not be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ProductDomainException("NEGATIVE_AMOUNT",
                    "Amount cannot be negative: " + amount);
        }
    }

    /**
     * Creates a Money value with the given amount and currency.
     */
    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    /**
     * Creates a Money value with the given amount in default currency (COP).
     */
    public static Money of(BigDecimal amount) {
        return new Money(amount, DEFAULT_CURRENCY);
    }
}
