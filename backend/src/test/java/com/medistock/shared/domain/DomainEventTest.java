package com.medistock.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DomainEvent marker interface")
class DomainEventTest {

    @Test
    @DisplayName("Should be an interface")
    void shouldBeAnInterface() {
        // Act & Assert
        assertTrue(DomainEvent.class.isInterface(),
                "DomainEvent must be a marker interface");
    }

    @Test
    @DisplayName("Should have no declared methods")
    void shouldHaveNoDeclaredMethods() {
        // Act & Assert
        assertEquals(0, DomainEvent.class.getDeclaredMethods().length,
                "DomainEvent must be a marker interface with zero methods");
    }

    @Test
    @DisplayName("Events should be identifiable via instanceof")
    void eventsShouldBeIdentifiableViaInstanceOf() {
        // Arrange
        record TestEvent() implements DomainEvent {}
        TestEvent event = new TestEvent();

        // Act & Assert
        assertInstanceOf(DomainEvent.class, event,
                "Records implementing DomainEvent must be identifiable via instanceof");
    }
}
