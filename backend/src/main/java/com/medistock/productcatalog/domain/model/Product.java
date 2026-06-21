package com.medistock.productcatalog.domain.model;

import com.medistock.shared.domain.AggregateRoot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Product aggregate root.
 * Represents a pharmaceutical product in the catalog.
 */
public class Product implements AggregateRoot {

    private final UUID id;
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
    private String imageUrl;
    private Money salePrice;
    private Money purchasePrice;
    private LocalDate expirationDate;
    private LocalDate manufacturingDate;
    private boolean isActive;

    private Product(UUID id, String tenantId, String name, String unit, String imageUrl,
                    LocalDate expirationDate, LocalDate manufacturingDate) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.unit = unit;
        this.imageUrl = imageUrl;
        this.expirationDate = expirationDate;
        this.manufacturingDate = manufacturingDate;
        this.isActive = true;
    }

    public static Product register(String tenantId, String name, String unit,
                                    LocalDate expirationDate, LocalDate manufacturingDate) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new ProductDomainException("tenantId must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new ProductDomainException("Product name must not be blank");
        }
        if (unit == null || unit.isBlank()) {
            throw new ProductDomainException("Unit must not be blank");
        }
        if (expirationDate == null) {
            throw new ProductDomainException("Expiration date must not be null");
        }
        if (manufacturingDate == null) {
            throw new ProductDomainException("Manufacturing date must not be null");
        }
        return new Product(UUID.randomUUID(), tenantId, name.trim(), unit.trim(), null, expirationDate, manufacturingDate);
    }

    /**
     * Reconstitutes a Product from persistence with the exact DB ID.
     * Unlike {@link #register(String, String, String)}, this does NOT generate a new UUID.
     */
    public static Product reconstitute(UUID id, String tenantId, String name, String unit, String imageUrl,
                                        LocalDate expirationDate, LocalDate manufacturingDate) {
        if (id == null) {
            throw new ProductDomainException("id must not be null");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new ProductDomainException("tenantId must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new ProductDomainException("Product name must not be blank");
        }
        if (unit == null || unit.isBlank()) {
            throw new ProductDomainException("Unit must not be blank");
        }
        if (expirationDate == null) {
            throw new ProductDomainException("Expiration date must not be null");
        }
        if (manufacturingDate == null) {
            throw new ProductDomainException("Manufacturing date must not be null");
        }
        return new Product(id, tenantId, name.trim(), unit.trim(), imageUrl, expirationDate, manufacturingDate);
    }

    public void update(String name, String genericName, UUID categoryId,
                       String concentration, String dosageForm, String manufacturer,
                       boolean requiresPrescription, boolean isControlled,
                       String storageCondition, String unit,
                       BigDecimal salePrice, BigDecimal purchasePrice,
                       String imageUrl,
                       LocalDate expirationDate, LocalDate manufacturingDate) {
        if (name == null || name.isBlank()) {
            throw new ProductDomainException("Product name must not be blank");
        }
        if (unit == null || unit.isBlank()) {
            throw new ProductDomainException("Unit must not be blank");
        }
        if (salePrice != null && salePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ProductDomainException("Sale price must not be negative");
        }
        if (expirationDate == null) {
            throw new ProductDomainException("Expiration date must not be null");
        }
        if (manufacturingDate == null) {
            throw new ProductDomainException("Manufacturing date must not be null");
        }

        this.name = name.trim();
        this.genericName = genericName;
        this.categoryId = categoryId;
        this.concentration = concentration;
        this.dosageForm = dosageForm;
        this.manufacturer = manufacturer;
        this.requiresPrescription = requiresPrescription;
        this.isControlled = isControlled;
        this.storageCondition = storageCondition;
        this.unit = unit.trim();
        this.imageUrl = imageUrl;
        this.expirationDate = expirationDate;
        this.manufacturingDate = manufacturingDate;
        this.salePrice = salePrice != null ? Money.of(salePrice) : Money.of(BigDecimal.ZERO);
        this.purchasePrice = purchasePrice != null ? Money.of(purchasePrice) : Money.of(BigDecimal.ZERO);
    }

    public void deactivate() {
        if (!isActive) {
            throw new ProductDomainException("Product is already inactive");
        }
        this.isActive = false;
    }

    public void reactivate() {
        if (isActive) {
            throw new ProductDomainException("Product is already active");
        }
        this.isActive = true;
    }

    // Getters
    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getGenericName() { return genericName; }
    public UUID getCategoryId() { return categoryId; }
    public String getConcentration() { return concentration; }
    public String getDosageForm() { return dosageForm; }
    public String getManufacturer() { return manufacturer; }
    public boolean isRequiresPrescription() { return requiresPrescription; }
    public boolean isControlled() { return isControlled; }
    public String getStorageCondition() { return storageCondition; }
    public String getUnit() { return unit; }
    public String getImageUrl() { return imageUrl; }
    public Money getSalePrice() { return salePrice; }
    public Money getPurchasePrice() { return purchasePrice; }
    public boolean isActive() { return isActive; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public LocalDate getManufacturingDate() { return manufacturingDate; }
}
