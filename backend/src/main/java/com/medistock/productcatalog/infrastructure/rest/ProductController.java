package com.medistock.productcatalog.infrastructure.rest;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.productcatalog.application.service.*;
import com.medistock.productcatalog.domain.model.ProductNotFoundException;
import com.medistock.productcatalog.domain.port.ProductRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final RegisterProductUseCase registerUseCase;
    private final UpdateProductUseCase updateUseCase;
    private final DeactivateProductUseCase deactivateUseCase;
    private final ReactivateProductUseCase reactivateUseCase;
    private final ProductRepository productRepository;

    public ProductController(RegisterProductUseCase registerUseCase,
                             UpdateProductUseCase updateUseCase,
                             DeactivateProductUseCase deactivateUseCase,
                             ReactivateProductUseCase reactivateUseCase,
                             ProductRepository productRepository) {
        this.registerUseCase = registerUseCase;
        this.updateUseCase = updateUseCase;
        this.deactivateUseCase = deactivateUseCase;
        this.reactivateUseCase = reactivateUseCase;
        this.productRepository = productRepository;
    }

    @PostMapping
    public ResponseEntity<ProductDTO> create(@Valid @RequestBody CreateProductRequest request) {
        String tenantId = TenantContext.getTenantId();
        ProductDTO result = registerUseCase.execute(tenantId, request.name(), request.unit());
        // Apply full update with all fields after registration
        ProductDTO updated = updateUseCase.execute(
                result.id(), request.name(), request.genericName(),
                request.categoryId(), request.concentration(), request.dosageForm(),
                request.manufacturer(), request.requiresPrescription(),
                request.isControlled(), request.storageCondition(),
                request.unit(), request.salePrice(), request.purchasePrice());
        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
    }

    @GetMapping
    public ResponseEntity<List<ProductDTO>> list(@RequestParam(required = false) Boolean active) {
        String tenantId = TenantContext.getTenantId();
        List<ProductDTO> products;
        if (active != null) {
            products = productRepository.findByTenantIdAndIsActive(tenantId, active).stream()
                    .map(p -> new ProductDTO(
                            p.getId(), p.getTenantId(), p.getName(), p.getGenericName(),
                            p.getCategoryId(), p.getConcentration(), p.getDosageForm(),
                            p.getManufacturer(), p.isRequiresPrescription(), p.isControlled(),
                            p.getStorageCondition(), p.getUnit(),
                            p.getSalePrice() != null ? p.getSalePrice().amount() : null,
                            p.getPurchasePrice() != null ? p.getPurchasePrice().amount() : null,
                            p.isActive()))
                    .toList();
        } else {
            products = productRepository.findByTenantId(tenantId).stream()
                    .map(p -> new ProductDTO(
                            p.getId(), p.getTenantId(), p.getName(), p.getGenericName(),
                            p.getCategoryId(), p.getConcentration(), p.getDosageForm(),
                            p.getManufacturer(), p.isRequiresPrescription(), p.isControlled(),
                            p.getStorageCondition(), p.getUnit(),
                            p.getSalePrice() != null ? p.getSalePrice().amount() : null,
                            p.getPurchasePrice() != null ? p.getPurchasePrice().amount() : null,
                            p.isActive()))
                    .toList();
        }
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getById(@PathVariable UUID id) {
        var product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        ProductDTO dto = new ProductDTO(
                product.getId(), product.getTenantId(), product.getName(), product.getGenericName(),
                product.getCategoryId(), product.getConcentration(), product.getDosageForm(),
                product.getManufacturer(), product.isRequiresPrescription(), product.isControlled(),
                product.getStorageCondition(), product.getUnit(),
                product.getSalePrice() != null ? product.getSalePrice().amount() : null,
                product.getPurchasePrice() != null ? product.getPurchasePrice().amount() : null,
                product.isActive());
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> update(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateProductRequest request) {
        ProductDTO result = updateUseCase.execute(
                id, request.name(), request.genericName(),
                request.categoryId(), request.concentration(), request.dosageForm(),
                request.manufacturer(), request.requiresPrescription(),
                request.isControlled(), request.storageCondition(),
                request.unit(), request.salePrice(), request.purchasePrice());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        deactivateUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<ProductDTO> reactivate(@PathVariable UUID id) {
        ProductDTO result = reactivateUseCase.execute(id);
        return ResponseEntity.ok(result);
    }
}
