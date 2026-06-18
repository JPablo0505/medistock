package com.medistock.productcatalog.infrastructure.persistence;

import com.medistock.shared.infrastructure.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity for the product_categories table.
 * Maps to the Category domain entity.
 */
@Entity
@Table(name = "product_categories", schema = "product_catalog")
public class JpaCategory extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // --- Constructors ---

    protected JpaCategory() {
        // JPA required
    }

    public JpaCategory(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // --- Getters / Setters ---

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
