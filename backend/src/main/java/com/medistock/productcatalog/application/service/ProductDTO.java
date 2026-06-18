package com.medistock.productcatalog.application.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Data Transfer Object for product operations.
 * Used for both request and response in the application layer.
 */
public record ProductDTO(
        UUID id,
        String tenantId,
        String name,
        String genericName,
        UUID categoryId,
        String concentration,
        String dosageForm,
        String manufacturer,
        boolean requiresPrescription,
        boolean isControlled,
        String storageCondition,
        String unit,
        BigDecimal salePrice,
        BigDecimal purchasePrice,
        boolean isActive
) {

    public static ProductDTOBuilder builder() {
        return new ProductDTOBuilder();
    }

    public static class ProductDTOBuilder {
        private UUID id;
        private String tenantId;
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
        private BigDecimal salePrice;
        private BigDecimal purchasePrice;
        private boolean isActive;

        ProductDTOBuilder() {}

        public ProductDTOBuilder id(UUID id) { this.id = id; return this; }
        public ProductDTOBuilder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public ProductDTOBuilder name(String name) { this.name = name; return this; }
        public ProductDTOBuilder genericName(String genericName) { this.genericName = genericName; return this; }
        public ProductDTOBuilder categoryId(UUID categoryId) { this.categoryId = categoryId; return this; }
        public ProductDTOBuilder concentration(String concentration) { this.concentration = concentration; return this; }
        public ProductDTOBuilder dosageForm(String dosageForm) { this.dosageForm = dosageForm; return this; }
        public ProductDTOBuilder manufacturer(String manufacturer) { this.manufacturer = manufacturer; return this; }
        public ProductDTOBuilder requiresPrescription(boolean requiresPrescription) { this.requiresPrescription = requiresPrescription; return this; }
        public ProductDTOBuilder isControlled(boolean isControlled) { this.isControlled = isControlled; return this; }
        public ProductDTOBuilder storageCondition(String storageCondition) { this.storageCondition = storageCondition; return this; }
        public ProductDTOBuilder unit(String unit) { this.unit = unit; return this; }
        public ProductDTOBuilder salePrice(BigDecimal salePrice) { this.salePrice = salePrice; return this; }
        public ProductDTOBuilder purchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; return this; }
        public ProductDTOBuilder isActive(boolean isActive) { this.isActive = isActive; return this; }

        public ProductDTO build() {
            return new ProductDTO(id, tenantId, name, genericName, categoryId,
                    concentration, dosageForm, manufacturer, requiresPrescription,
                    isControlled, storageCondition, unit, salePrice, purchasePrice, isActive);
        }
    }
}
