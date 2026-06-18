package com.medistock.inventory.domain.model;

/**
 * Lifecycle status of an inventory batch.
 * <ul>
 *   <li>{@code ACTIVE} — available and not expired</li>
 *   <li>{@code EXPIRED} — past expiration date</li>
 *   <li>{@code QUARANTINED} — temporarily blocked</li>
 *   <li>{@code DEPLETED} — fully allocated (currentQuantity = 0)</li>
 *   <li>{@code DISPOSED} — written off or destroyed</li>
 * </ul>
 */
public enum BatchStatus {
    ACTIVE,
    EXPIRED,
    QUARANTINED,
    DEPLETED,
    DISPOSED
}
