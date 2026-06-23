package com.medistock.productcatalog.infrastructure.rest;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.productcatalog.domain.model.Category;
import com.medistock.productcatalog.domain.port.CategoryRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private static final String DEFAULT_TENANT = "default";

    private static String resolveTenantId() {
        String tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : DEFAULT_TENANT;
    }

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        String tenantId = resolveTenantId();
        Category category = Category.create(tenantId, request.name(), request.description());
        Category saved = categoryRepository.save(category);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CategoryResponse(saved.getId(), saved.getName(),
                        saved.getDescription(), null, null));
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> list() {
        String tenantId = resolveTenantId();
        List<CategoryResponse> categories = categoryRepository.findByTenantId(tenantId).stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(),
                        c.getDescription(), null, null))
                .toList();
        return ResponseEntity.ok(categories);
    }
}
