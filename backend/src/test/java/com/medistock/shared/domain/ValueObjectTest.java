package com.medistock.shared.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED phase: Verify ValueObject is a marker interface.
 * Value objects are typically implemented as Java records.
 */
class ValueObjectTest {

    @Test
    void shouldBeAnInterface() {
        assertTrue(ValueObject.class.isInterface(),
            "ValueObject must be a marker interface");
    }

    @Test
    void shouldHaveNoDeclaredMethods() {
        assertEquals(0, ValueObject.class.getDeclaredMethods().length,
            "ValueObject must be a marker interface with zero methods");
    }

    @Test
    void valueObjectsShouldBeIdentifiableViaInstanceOf() {
        record Money(java.math.BigDecimal amount, String currency) implements ValueObject {}
        Money money = new Money(java.math.BigDecimal.TEN, "USD");
        assertInstanceOf(ValueObject.class, money,
            "Records implementing ValueObject must be identifiable via instanceof");
    }
}
