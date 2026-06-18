package com.medistock.productcatalog.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Lightweight entity representing a product category.
 */
public class Category {

    private final UUID categoryId;
    private final String tenantId;
    private String name;
    private String description;

    private Category(UUID categoryId, String tenantId, String name, String description) {
        this.categoryId = categoryId;
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
    }

    /**
     * Factory method to create a new Category.
     */
    public static Category create(String tenantId, String name, String description) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (tenantId.isBlank()) {
            throw new ProductDomainException("TENANT_REQUIRED", "tenantId must not be blank");
        }
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new ProductDomainException("INVALID_NAME", "Category name must not be blank");
        }
        if (name.length() > 100) {
            throw new ProductDomainException("INVALID_NAME", "Category name must not exceed 100 characters");
        }

        return new Category(UUID.randomUUID(), tenantId, name.trim(), description);
    }

    /**
     * Creates a Category with an existing ID (for reconstitution from persistence).
     */
    public static Category reconstitute(UUID categoryId, String tenantId, String name, String description) {
        return new Category(categoryId, tenantId, name, description);
    }

    public void update(String name, String description) {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new ProductDomainException("INVALID_NAME", "Category name must not be blank");
        }
        if (name.length() > 100) {
            throw new ProductDomainException("INVALID_NAME", "Category name must not exceed 100 characters");
        }
        this.name = name.trim();
        this.description = description;
    }

    // --- Getters ---

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Category category = (Category) o;
        return Objects.equals(categoryId, category.categoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryId);
    }
}
