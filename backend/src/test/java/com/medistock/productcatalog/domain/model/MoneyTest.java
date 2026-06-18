package com.medistock.productcatalog.domain.model;

import com.medistock.shared.domain.ValueObject;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void shouldCreateMoneyWithValidAmount() {
        Money money = Money.of(100.50);
        assertEquals(0, BigDecimal.valueOf(100.50).compareTo(money.amount()));
        assertEquals("COP", money.currency());
    }

    @Test
    void shouldCreateMoneyWithZeroAmount() {
        Money money = Money.of(BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ZERO.compareTo(money.amount()));
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(BigDecimal.valueOf(-1)));
    }

    @Test
    void shouldRejectNullAmount() {
        assertThrows(NullPointerException.class,
                () -> new Money(null, "COP"));
    }

    @Test
    void shouldRejectNullCurrency() {
        assertThrows(NullPointerException.class,
                () -> new Money(BigDecimal.TEN, null));
    }

    @Test
    void twoMoneysWithSameValueShouldBeEqual() {
        Money a = Money.of(50.00);
        Money b = Money.of(50.00);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void twoMoneysWithDifferentValuesShouldNotBeEqual() {
        Money a = Money.of(50.00);
        Money b = Money.of(100.00);
        assertNotEquals(a, b);
    }

    @Test
    void shouldBeARecord() {
        Money money = Money.of(10.00);
        assertInstanceOf(ValueObject.class, money);
    }
}
