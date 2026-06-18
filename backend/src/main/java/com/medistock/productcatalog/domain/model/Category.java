package com.medistock.productcatalog.domain.model;

import java.util.UUID;

/**
 * Product category entity.
 */
public class Category {

    private final UUID id;
    private final String tenantId;
    private String name;
    private String description;

    public Category(UUID id, String tenantId, String name, String description) {
        this.id = id;
        this.tenantId = tenantId;
        setName(name);
        this.description = description;
    }

    public static Category create(String tenantId, String name, String description) {
        return new Category(UUID.randomUUID(), tenantId, name, description);
    }

    public void update(String name, String description) {
        setName(name);
        this.description = description;
    }

    private void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        this.name = name.trim();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
