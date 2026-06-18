package com.medistock.productcatalog.domain.model;

import com.medistock.shared.domain.AggregateRoot;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for the Product bounded context.
 * Enforces invariants: name, tenantId, categoryId, unit, salePrice, purchasePrice are required.
 */
public class Product implements AggregateRoot {

    private final UUID productId;
    private final String tenantId;
    private String name;
    private String genericName;
    private UUID categoryId;
    private String concentration;
    private String dosageForm;
    private String manufacturer;
    private boolean requiresPrescription;
    private boolean isControlled;
    private String storageCondition;
    private String unit;
    private Money salePrice;
    private Money purchasePrice;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Product(UUID productId, String tenantId, String name, String genericName,
                    UUID categoryId, String concentration, String dosageForm,
                    String manufacturer, boolean requiresPrescription, boolean isControlled,
                    String storageCondition, String unit, Money salePrice, Money purchasePrice,
                    boolean isActive, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.productId = productId;
        this.tenantId = tenantId;
        this.name = name;
        this.genericName = genericName;
        this.categoryId = categoryId;
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
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Factory method to register a new product with validation.
     */
    public static Product register(String tenantId, String name, String genericName,
                                   UUID categoryId, String concentration, String dosageForm,
                                   String manufacturer, boolean requiresPrescription,
                                   boolean isControlled, String storageCondition, String unit,
                                   Money salePrice, Money purchasePrice) {
        validateRequiredFields(tenantId, name, categoryId, unit, salePrice, purchasePrice);

        LocalDateTime now = LocalDateTime.now();
        return new Product(
                UUID.randomUUID(),
                tenantId,
                name.trim(),
                genericName != null ? genericName.trim() : null,
                categoryId,
                concentration != null ? concentration.trim() : null,
                dosageForm != null ? dosageForm.trim() : null,
                manufacturer != null ? manufacturer.trim() : null,
                requiresPrescription,
                isControlled,
                storageCondition != null ? storageCondition.trim() : null,
                unit.trim(),
                salePrice,
                purchasePrice,
                true,
                now,
                now
        );
    }

    /**
     * Reconstitute a product from persistence (bypasses validation for trusted data).
     */
    public static Product reconstitute(UUID productId, String tenantId, String name,
                                       String genericName, UUID categoryId,
                                       String concentration, String dosageForm,
                                       String manufacturer, boolean requiresPrescription,
                                       boolean isControlled, String storageCondition,
                                       String unit, Money salePrice, Money purchasePrice,
                                       boolean isActive, LocalDateTime createdAt,
                                       LocalDateTime updatedAt) {
        return new Product(productId, tenantId, name, genericName, categoryId,
                concentration, dosageForm, manufacturer, requiresPrescription,
                isControlled, storageCondition, unit, salePrice, purchasePrice,
                isActive, createdAt, updatedAt);
    }

    /**
     * Updates product mutable fields.
     */
    public void update(String name, String genericName, UUID categoryId,
                       String concentration, String dosageForm, String manufacturer,
                       boolean requiresPrescription, boolean isControlled,
                       String storageCondition, String unit,
                       Money salePrice, Money purchasePrice) {
        validateRequiredFields(this.tenantId, name, categoryId, unit, salePrice, purchasePrice);

        this.name = name.trim();
        this.genericName = genericName != null ? genericName.trim() : null;
        this.categoryId = categoryId;
        this.concentration = concentration != null ? concentration.trim() : null;
        this.dosageForm = dosageForm != null ? dosageForm.trim() : null;
        this.manufacturer = manufacturer != null ? manufacturer.trim() : null;
        this.requiresPrescription = requiresPrescription;
        this.isControlled = isControlled;
        this.storageCondition = storageCondition != null ? storageCondition.trim() : null;
        this.unit = unit.trim();
        this.salePrice = salePrice;
        this.purchasePrice = purchasePrice;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Soft-deletes this product by setting isActive to false.
     */
    public void deactivate() {
        if (!this.isActive) {
            return; // Idempotent
        }
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Reactivates this product by setting isActive to true.
     */
    public void reactivate() {
        if (this.isActive) {
            return; // Idempotent
        }
        this.isActive = true;
        this.updatedAt = LocalDateTime.now();
    }

    private static void validateRequiredFields(String tenantId, String name,
                                                UUID categoryId, String unit,
                                                Money salePrice, Money purchasePrice) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new ProductDomainException("TENANT_REQUIRED", "Tenant ID must not be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new ProductDomainException("INVALID_NAME", "Product name must not be null or blank");
        }
        if (name.length() > 200) {
            throw new ProductDomainException("INVALID_NAME", "Product name must not exceed 200 characters");
        }
        if (categoryId == null) {
            throw new ProductDomainException("INVALID_CATEGORY", "Category ID must not be null");
        }
        if (unit == null || unit.isBlank()) {
            throw new ProductDomainException("INVALID_UNIT", "Unit must not be null or blank");
        }
        if (salePrice == null) {
            throw new ProductDomainException("INVALID_SALE_PRICE", "Sale price must not be null");
        }
        if (purchasePrice == null) {
            throw new ProductDomainException("INVALID_PURCHASE_PRICE", "Purchase price must not be null");
        }
    }

    // --- Getters ---

    public UUID getProductId() {
        return productId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getGenericName() {
        return genericName;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getConcentration() {
        return concentration;
    }

    public String getDosageForm() {
        return dosageForm;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public boolean isRequiresPrescription() {
        return requiresPrescription;
    }

    public boolean isControlled() {
        return isControlled;
    }

    public String getStorageCondition() {
        return storageCondition;
    }

    public String getUnit() {
        return unit;
    }

    public Money getSalePrice() {
        return salePrice;
    }

    public Money getPurchasePrice() {
        return purchasePrice;
    }

    public boolean isActive() {
        return isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(productId, product.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId);
    }
}
