package com.medistock.productcatalog.infrastructure.rest;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.productcatalog.application.service.*;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.model.ProductNotFoundException;
import com.medistock.productcatalog.domain.port.ProductRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final RegisterProductUseCase registerUseCase;
    private final UpdateProductUseCase updateUseCase;
    private final DeactivateProductUseCase deactivateUseCase;
    private final ReactivateProductUseCase reactivateUseCase;
    private final ProductRepository productRepository;
    private final BatchRepository batchRepository;

    public ProductController(RegisterProductUseCase registerUseCase,
                             UpdateProductUseCase updateUseCase,
                             DeactivateProductUseCase deactivateUseCase,
                             ReactivateProductUseCase reactivateUseCase,
                             ProductRepository productRepository,
                             BatchRepository batchRepository) {
        this.registerUseCase = registerUseCase;
        this.updateUseCase = updateUseCase;
        this.deactivateUseCase = deactivateUseCase;
        this.reactivateUseCase = reactivateUseCase;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
    }

    @PostMapping
    public ResponseEntity<ProductDTO> create(@Valid @RequestBody CreateProductRequest request) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) tenantId = "default";
        ProductDTO result = registerUseCase.execute(tenantId, request.name(), request.unit(),
                request.expirationDate(), request.manufacturingDate());
        // Apply full update with all fields after registration
        ProductDTO updated = updateUseCase.execute(
                result.id(), request.name(), request.genericName(),
                request.categoryId(), request.concentration(), request.dosageForm(),
                request.manufacturer(), request.requiresPrescription(),
                request.isControlled(), request.storageCondition(),
                request.unit(), request.salePrice(), request.purchasePrice(),
                request.imageUrl(), request.expirationDate(), request.manufacturingDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
    }

    @GetMapping
    public ResponseEntity<List<ProductDTO>> list(@RequestParam(required = false) Boolean active) {
        String tenantId = TenantContext.getTenantId();
        Map<UUID, Integer> stockMap = computeStockMap(tenantId);

        List<ProductDTO> products;
        if (active != null) {
            List<Product> source = tenantId != null
                    ? productRepository.findByTenantIdAndIsActive(tenantId, active)
                    : productRepository.findAll().stream()
                        .filter(p -> p.isActive() == active)
                        .toList();
            products = source.stream()
                    .map(p -> toDTO(p, stockMap.getOrDefault(p.getId(), 0)))
                    .toList();
        } else {
            List<Product> source = tenantId != null
                    ? productRepository.findByTenantId(tenantId)
                    : productRepository.findAll();
            products = source.stream()
                    .map(p -> toDTO(p, stockMap.getOrDefault(p.getId(), 0)))
                    .toList();
        }
        return ResponseEntity.ok(products);
    }

    private Map<UUID, Integer> computeStockMap(String tenantId) {
        List<com.medistock.inventory.domain.model.Batch> batches = tenantId != null
                ? batchRepository.findAllByTenantId(tenantId)
                : batchRepository.findAll();
        return batches.stream()
                .collect(Collectors.groupingBy(
                        com.medistock.inventory.domain.model.Batch::getProductId,
                        Collectors.summingInt(com.medistock.inventory.domain.model.Batch::getCurrentQuantity)
                ));
    }

    private ProductDTO toDTO(Product p, int stockQuantity) {
        return new ProductDTO(
                p.getId(), p.getTenantId(), p.getName(), p.getGenericName(),
                p.getCategoryId(), p.getConcentration(), p.getDosageForm(),
                p.getManufacturer(), p.isRequiresPrescription(), p.isControlled(),
                p.getStorageCondition(), p.getUnit(), p.getImageUrl(),
                p.getSalePrice() != null ? p.getSalePrice().amount() : null,
                p.getPurchasePrice() != null ? p.getPurchasePrice().amount() : null,
                p.isActive(),
                p.getExpirationDate(), p.getManufacturingDate(),
                stockQuantity
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getById(@PathVariable UUID id) {
        var product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        String tenantId = TenantContext.getTenantId();
        int stockQuantity = computeStockForProduct(id, tenantId);
        ProductDTO dto = toDTO(product, stockQuantity);
        return ResponseEntity.ok(dto);
    }

    private int computeStockForProduct(UUID productId, String tenantId) {
        List<com.medistock.inventory.domain.model.Batch> batches = tenantId != null
                ? batchRepository.findAllByTenantId(tenantId)
                : batchRepository.findAll();
        return batches.stream()
                .filter(b -> b.getProductId().equals(productId))
                .collect(Collectors.summingInt(
                        com.medistock.inventory.domain.model.Batch::getCurrentQuantity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> update(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateProductRequest request) {
        ProductDTO result = updateUseCase.execute(
                id, request.name(), request.genericName(),
                request.categoryId(), request.concentration(), request.dosageForm(),
                request.manufacturer(), request.requiresPrescription(),
                request.isControlled(), request.storageCondition(),
                request.unit(), request.salePrice(), request.purchasePrice(),
                request.imageUrl(), request.expirationDate(), request.manufacturingDate());
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
