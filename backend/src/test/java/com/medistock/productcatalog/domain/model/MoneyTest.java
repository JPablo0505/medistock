package com.medistock.productcatalog.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void shouldCreateMoneyWithValidAmount() {
        Money money = Money.of(new BigDecimal("1500.50"), "COP");
        assertEquals(new BigDecimal("1500.50"), money.amount());
        assertEquals("COP", money.currency());
    }

    @Test
    void shouldDefaultToCopCurrency() {
        Money money = Money.of(new BigDecimal("500.00"));
        assertEquals("COP", money.currency());
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThrows(ProductDomainException.class,
                () -> Money.of(new BigDecimal("-100.00"), "COP"));
    }

    @Test
    void shouldRejectNullAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(null, "COP"));
    }

    @Test
    void shouldRejectNullCurrency() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(BigDecimal.TEN, null));
    }

    @Test
    void shouldAcceptZeroAmount() {
        Money money = Money.of(BigDecimal.ZERO, "COP");
        assertEquals(BigDecimal.ZERO, money.amount());
    }

    @Test
    void shouldImplementValueObject() {
        Money money = Money.of(new BigDecimal("100.00"), "COP");
        assertTrue(money instanceof com.medistock.shared.domain.ValueObject);
    }

    @Test
    void shouldBeEqualForSameAmountAndCurrency() {
        Money money1 = Money.of(new BigDecimal("100.00"), "COP");
        Money money2 = Money.of(new BigDecimal("100.00"), "COP");
        assertEquals(money1, money2);
        assertEquals(money1.hashCode(), money2.hashCode());
    }

    @Test
    void shouldNotBeEqualForDifferentAmounts() {
        Money money1 = Money.of(new BigDecimal("100.00"), "COP");
        Money money2 = Money.of(new BigDecimal("200.00"), "COP");
        assertNotEquals(money1, money2);
    }

    @Test
    void shouldNotBeEqualForDifferentCurrencies() {
        Money money1 = Money.of(new BigDecimal("100.00"), "COP");
        Money money2 = Money.of(new BigDecimal("100.00"), "USD");
        assertNotEquals(money1, money2);
    }
}
