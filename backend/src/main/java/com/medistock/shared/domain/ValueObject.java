package com.medistock.shared.domain;

/**
 * Marker interface for value objects.
 * Value objects are immutable, equality-based domain primitives.
 * They are typically implemented as Java records.
 *
 * <p>This is a pure marker interface with zero methods — the contract for value objects
 * (immutability, equality by value) is enforced by convention and code review.</p>
 */
public interface ValueObject {
}
