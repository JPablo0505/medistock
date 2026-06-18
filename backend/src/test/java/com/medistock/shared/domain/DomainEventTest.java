package com.medistock.shared.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED phase: Verify DomainEvent is a marker interface.
 * Domain events carry no forced structure — they are payload-agnostic.
 */
class DomainEventTest {

    @Test
    void shouldBeAnInterface() {
        assertTrue(DomainEvent.class.isInterface(),
            "DomainEvent must be a marker interface");
    }

    @Test
    void shouldHaveNoDeclaredMethods() {
        assertEquals(0, DomainEvent.class.getDeclaredMethods().length,
            "DomainEvent must be a marker interface with zero methods");
    }

    @Test
    void eventsShouldBeIdentifiableViaInstanceOf() {
        record TestEvent() implements DomainEvent {}
        TestEvent event = new TestEvent();
        assertInstanceOf(DomainEvent.class, event,
            "Records implementing DomainEvent must be identifiable via instanceof");
    }
}
