package com.medistock.shared.domain;

/**
 * Marker interface for domain aggregate roots.
 * Implementing this interface signals that a class is the root entity of an aggregate,
 * responsible for enforcing invariants and controlling access to child entities.
 *
 * <p>This is a pure marker interface with zero methods — its purpose is type metadata
 * and architectural enforcement via ArchUnit tests.</p>
 */
public interface AggregateRoot {
}
