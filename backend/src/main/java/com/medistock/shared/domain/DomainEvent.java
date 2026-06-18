package com.medistock.shared.domain;

/**
 * Marker interface for domain events.
 * Domain events represent something meaningful that happened in the domain.
 * Implementing this interface signals that a record/class is an event payload.
 *
 * <p>This is a pure marker interface with zero methods — events are payload-agnostic
 * and carry their own structure based on the domain concept they represent.</p>
 */
public interface DomainEvent {
}
