package com.medistock.shared.infrastructure;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Abstract JPA base entity providing common fields for all entities:
 * id (UUID), tenantId, createdAt, updatedAt, and version (optimistic locking).
 *
 * <p>All domain entities in the system extend this class to ensure consistent
 * audit fields and optimistic locking behavior across all bounded contexts.</p>
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 36)
    private String tenantId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = LocalDateTime.now(Clock.systemDefaultZone());
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(Clock.systemDefaultZone());
    }

    // --- Getters ---

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    // --- Setters (protected for infrastructure use) ---

    public void setId(UUID id) {
        this.id = id;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
