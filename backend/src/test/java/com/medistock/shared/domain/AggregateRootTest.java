package com.medistock.shared.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED phase: Verify AggregateRoot is a marker interface.
 * A marker interface has no methods — its purpose is type metadata.
 * Classes implementing AggregateRoot should be identifiable via instanceof.
 */
class AggregateRootTest {

    @Test
    void shouldBeAnInterface() {
        assertTrue(AggregateRoot.class.isInterface(),
            "AggregateRoot must be a marker interface");
    }

    @Test
    void shouldHaveNoDeclaredMethods() {
        assertEquals(0, AggregateRoot.class.getDeclaredMethods().length,
            "AggregateRoot must be a marker interface with zero methods");
    }

    @Test
    void shouldSupportInstanceOfCheck() {
        // Simulate an aggregate that implements the marker
        class TestAggregate implements AggregateRoot {}
        TestAggregate aggregate = new TestAggregate();
        assertInstanceOf(AggregateRoot.class, aggregate,
            "Classes implementing AggregateRoot must be identifiable via instanceof");
    }

    @Test
    void nonAggregatesShouldNotBeInstanceOfAggregateRoot() {
        class NotAnAggregate {}
        NotAnAggregate obj = new NotAnAggregate();
        assertFalse(obj instanceof AggregateRoot,
            "Classes NOT implementing AggregateRoot should not pass instanceof check");
    }
}
