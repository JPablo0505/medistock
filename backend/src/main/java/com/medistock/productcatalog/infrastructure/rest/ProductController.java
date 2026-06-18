package com.medistock.productcatalog.infrastructure.rest;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.productcatalog.application.service.DeactivateProductUseCase;
import com.medistock.productcatalog.application.service.ProductDTO;
import com.medistock.productcatalog.application.service.ReactivateProductUseCase;
import com.medistock.productcatalog.application.service.RegisterProductUseCase;
import com.medistock.productcatalog.application.service.UpdateProductUseCase;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.model.ProductDomainException;
import com.medistock.productcatalog.domain.model.ProductNotFoundException;
import com.medistock.productcatalog.domain.port.ProductRepository;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Product CRUD operations.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final RegisterProductUseCase registerProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeactivateProductUseCase deactivateProductUseCase;
    private final ReactivateProductUseCase reactivateProductUseCase;
    private final ProductRepository productRepository;

    public ProductController(RegisterProductUseCase registerProductUseCase,
                             UpdateProductUseCase updateProductUseCase,
                             DeactivateProductUseCase deactivateProductUseCase,
                             ReactivateProductUseCase reactivateProductUseCase,
                             ProductRepository productRepository) {
        this.registerProductUseCase = registerProductUseCase;
        this.updateProductUseCase = updateProductUseCase;
        this.deactivateProductUseCase = deactivateProductUseCase;
        this.reactivateProductUseCase = reactivateProductUseCase;
        this.productRepository = productRepository;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ProductDomainException(
                    "TENANT_REQUIRED", "Tenant ID is required");
        }

        ProductDTO dto = registerProductUseCase.execute(
                tenantId,
                request.name(),
                request.genericName(),
                request.categoryId(),
                request.concentration(),
                request.dosageForm(),
                request.manufacturer(),
                request.requiresPrescription(),
                request.isControlled(),
                request.storageCondition(),
                request.unit(),
                request.salePrice(),
                request.purchasePrice()
        );

        URI location = URI.create("/api/v1/products/" + dto.id());
        return ResponseEntity.created(location).body(ProductResponse.fromDTO(dto));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> listProducts() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.ok(List.of());
        }

        List<Product> products = productRepository.findByTenantId(tenantId);
        List<ProductResponse> responses = products.stream()
                .map(p -> ProductResponse.fromDTO(RegisterProductUseCase.toDTO(p)))
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return ResponseEntity.ok(
                ProductResponse.fromDTO(RegisterProductUseCase.toDTO(product)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable UUID id,
                                                         @Valid @RequestBody UpdateProductRequest request) {
        ProductDTO dto = updateProductUseCase.execute(
                id,
                request.name(),
                request.genericName(),
                request.categoryId(),
                request.concentration(),
                request.dosageForm(),
                request.manufacturer(),
                request.requiresPrescription(),
                request.isControlled(),
                request.storageCondition(),
                request.unit(),
                request.salePrice(),
                request.purchasePrice()
        );
        return ResponseEntity.ok(ProductResponse.fromDTO(dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ProductResponse> deactivateProduct(@PathVariable UUID id) {
        ProductDTO dto = deactivateProductUseCase.execute(id);
        return ResponseEntity.ok(ProductResponse.fromDTO(dto));
    }

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<ProductResponse> reactivateProduct(@PathVariable UUID id) {
        ProductDTO dto = reactivateProductUseCase.execute(id);
        return ResponseEntity.ok(ProductResponse.fromDTO(dto));
    }
}
