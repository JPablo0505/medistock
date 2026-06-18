package com.medistock.productcatalog.infrastructure.persistence;

import com.medistock.shared.infrastructure.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPA entity for the products table in the product_catalog schema.
 * Maps to the Product domain aggregate.
 */
@Entity
@Table(name = "products", schema = "product_catalog")
public class JpaProduct extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "generic_name", length = 200)
    private String genericName;

    @Column(name = "category_id", nullable = false, length = 36)
    private String categoryId;

    @Column(length = 100)
    private String concentration;

    @Column(name = "dosage_form", length = 50)
    private String dosageForm;

    @Column(length = 200)
    private String manufacturer;

    @Column(name = "requires_prescription", nullable = false)
    private boolean requiresPrescription;

    @Column(name = "is_controlled", nullable = false)
    private boolean isControlled;

    @Column(name = "storage_condition", length = 50)
    private String storageCondition;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "purchase_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    // --- Constructors ---

    protected JpaProduct() {
        // JPA required
    }

    public JpaProduct(String name, String genericName, UUID categoryId,
                      String concentration, String dosageForm, String manufacturer,
                      boolean requiresPrescription, boolean isControlled,
                      String storageCondition, String unit,
                      BigDecimal salePrice, BigDecimal purchasePrice,
                      boolean isActive) {
        this.name = name;
        this.genericName = genericName;
        this.categoryId = categoryId != null ? categoryId.toString() : null;
        this.concentration = concentration;
        this.dosageForm = dosageForm;
        this.manufacturer = manufacturer;
        this.requiresPrescription = requiresPrescription;
        this.isControlled = isControlled;
        this.storageCondition = storageCondition;
        this.unit = unit;
        this.salePrice = salePrice;
        this.purchasePrice = purchasePrice;
        this.isActive = isActive;
    }

    // --- Getters / Setters ---

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGenericName() {
        return genericName;
    }

    public void setGenericName(String genericName) {
        this.genericName = genericName;
    }

    public UUID getCategoryId() {
        return categoryId != null ? UUID.fromString(categoryId) : null;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId != null ? categoryId.toString() : null;
    }

    public String getConcentration() {
        return concentration;
    }

    public void setConcentration(String concentration) {
        this.concentration = concentration;
    }

    public String getDosageForm() {
        return dosageForm;
    }

    public void setDosageForm(String dosageForm) {
        this.dosageForm = dosageForm;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public boolean isRequiresPrescription() {
        return requiresPrescription;
    }

    public void setRequiresPrescription(boolean requiresPrescription) {
        this.requiresPrescription = requiresPrescription;
    }

    public boolean isControlled() {
        return isControlled;
    }

    public void setControlled(boolean controlled) {
        isControlled = controlled;
    }

    public String getStorageCondition() {
        return storageCondition;
    }

    public void setStorageCondition(String storageCondition) {
        this.storageCondition = storageCondition;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
