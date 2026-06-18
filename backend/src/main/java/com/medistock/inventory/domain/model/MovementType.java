package com.medistock.inventory.domain.model;

/**
 * Type of inventory movement recorded in the immutable ledger.
 */
public enum MovementType {
    RECEIPT,
    ADJUSTMENT,
    WRITE_OFF,
    TRANSFER,
    ALLOCATED,
    QUARANTINE_IN,
    QUARANTINE_OUT
}
