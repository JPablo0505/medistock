package com.medistock.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AggregateRoot marker interface")
class AggregateRootTest {

    @Test
    @DisplayName("Should be an interface")
    void shouldBeAnInterface() {
        // Act & Assert
        assertTrue(AggregateRoot.class.isInterface(),
                "AggregateRoot must be a marker interface");
    }

    @Test
    @DisplayName("Should have no declared methods")
    void shouldHaveNoDeclaredMethods() {
        // Act & Assert
        assertEquals(0, AggregateRoot.class.getDeclaredMethods().length,
                "AggregateRoot must be a marker interface with zero methods");
    }

    @Test
    @DisplayName("Should support instanceof check")
    void shouldSupportInstanceOfCheck() {
        // Arrange
        class TestAggregate implements AggregateRoot {}
        TestAggregate aggregate = new TestAggregate();

        // Act & Assert
        assertInstanceOf(AggregateRoot.class, aggregate,
                "Classes implementing AggregateRoot must be identifiable via instanceof");
    }

    @Test
    @DisplayName("Non-aggregates should not be instance of AggregateRoot")
    void nonAggregatesShouldNotBeInstanceOfAggregateRoot() {
        // Arrange
        class NotAnAggregate {}
        NotAnAggregate obj = new NotAnAggregate();

        // Act & Assert
        assertFalse(obj instanceof AggregateRoot,
                "Classes NOT implementing AggregateRoot should not pass instanceof check");
    }
}
