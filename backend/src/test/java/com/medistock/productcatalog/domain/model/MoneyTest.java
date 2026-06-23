package com.medistock.productcatalog.domain.model;

import com.medistock.shared.domain.ValueObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Money value object")
class MoneyTest {

    @Test
    @DisplayName("Should create money with valid amount and default currency")
    void shouldCreateMoneyWithValidAmount() {
        // Arrange & Act
        Money money = Money.of(100.50);

        // Assert
        assertEquals(0, BigDecimal.valueOf(100.50).compareTo(money.amount()), "amount");
        assertEquals("COP", money.currency(), "default currency");
    }

    @Test
    @DisplayName("Should create money with zero amount")
    void shouldCreateMoneyWithZeroAmount() {
        // Arrange & Act
        Money money = Money.of(BigDecimal.ZERO);

        // Assert
        assertEquals(0, BigDecimal.ZERO.compareTo(money.amount()), "amount should be zero");
    }

    @Test
    @DisplayName("Should reject negative amount")
    void shouldRejectNegativeAmount() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(BigDecimal.valueOf(-1)),
                "Negative amount should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should reject null amount")
    void shouldRejectNullAmount() {
        // Act & Assert
        assertThrows(NullPointerException.class,
                () -> new Money(null, "COP"),
                "Null amount should throw NullPointerException");
    }

    @Test
    @DisplayName("Should reject null currency")
    void shouldRejectNullCurrency() {
        // Act & Assert
        assertThrows(NullPointerException.class,
                () -> new Money(BigDecimal.TEN, null),
                "Null currency should throw NullPointerException");
    }

    @Test
    @DisplayName("Two moneys with same value should be equal")
    void twoMoneysWithSameValueShouldBeEqual() {
        // Arrange
        Money a = Money.of(50.00);
        Money b = Money.of(50.00);

        // Assert
        assertEquals(a, b, "equal values should be equal");
        assertEquals(a.hashCode(), b.hashCode(), "equal values should have same hash code");
    }

    @Test
    @DisplayName("Two moneys with different values should not be equal")
    void twoMoneysWithDifferentValuesShouldNotBeEqual() {
        // Arrange
        Money a = Money.of(50.00);
        Money b = Money.of(100.00);

        // Assert
        assertNotEquals(a, b, "different values should not be equal");
    }

    @Test
    @DisplayName("Should implement ValueObject interface")
    void shouldBeARecord() {
        // Arrange & Act
        Money money = Money.of(10.00);

        // Assert
        assertInstanceOf(ValueObject.class, money, "Money should implement ValueObject");
    }
}
