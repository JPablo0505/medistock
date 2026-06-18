package com.medistock.productcatalog.infrastructure.rest;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.productcatalog.domain.model.Category;
import com.medistock.productcatalog.domain.model.ProductDomainException;
import com.medistock.productcatalog.domain.port.CategoryRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Category CRUD operations.
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ProductDomainException("TENANT_REQUIRED", "Tenant ID is required");
        }

        Category category = Category.create(tenantId, request.name(), request.description());
        Category saved = categoryRepository.save(category);

        URI location = URI.create("/api/v1/categories/" + saved.getCategoryId());
        return ResponseEntity.created(location).body(new CategoryResponse(
                saved.getCategoryId(), saved.getName(), saved.getDescription()));
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.ok(List.of());
        }

        List<Category> categories = categoryRepository.findByTenantId(tenantId);
        List<CategoryResponse> responses = categories.stream()
                .map(c -> new CategoryResponse(c.getCategoryId(), c.getName(), c.getDescription()))
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Request DTO for creating a category.
     */
    public record CreateCategoryRequest(
            @NotBlank(message = "Category name is required")
            @Size(max = 100, message = "Category name must not exceed 100 characters")
            String name,

            @Size(max = 500, message = "Description must not exceed 500 characters")
            String description
    ) {}

    /**
     * Response DTO for category.
     */
    public record CategoryResponse(
            UUID id,
            String name,
            String description
    ) {}
}
