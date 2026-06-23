package com.medistock.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValueObject marker interface")
class ValueObjectTest {

    @Test
    @DisplayName("Should be an interface")
    void shouldBeAnInterface() {
        // Act & Assert
        assertTrue(ValueObject.class.isInterface(),
                "ValueObject must be a marker interface");
    }

    @Test
    @DisplayName("Should have no declared methods")
    void shouldHaveNoDeclaredMethods() {
        // Act & Assert
        assertEquals(0, ValueObject.class.getDeclaredMethods().length,
                "ValueObject must be a marker interface with zero methods");
    }

    @Test
    @DisplayName("Value objects should be identifiable via instanceof")
    void valueObjectsShouldBeIdentifiableViaInstanceOf() {
        // Arrange
        record Money(java.math.BigDecimal amount, String currency) implements ValueObject {}
        Money money = new Money(java.math.BigDecimal.TEN, "USD");

        // Act & Assert
        assertInstanceOf(ValueObject.class, money,
                "Records implementing ValueObject must be identifiable via instanceof");
    }
}
