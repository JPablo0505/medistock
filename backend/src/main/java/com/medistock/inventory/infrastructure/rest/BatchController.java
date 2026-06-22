package com.medistock.inventory.infrastructure.rest;

import com.medistock.crosscutting.security.TenantContext;
import com.medistock.inventory.application.service.*;
import com.medistock.inventory.domain.model.*;
import com.medistock.inventory.domain.port.BatchRepository;
import com.medistock.inventory.domain.port.MovementRepository;
import com.medistock.productcatalog.domain.model.Product;
import com.medistock.productcatalog.domain.port.ProductRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {

    private final ReceiveStockUseCase receiveStockUseCase;
    private final AdjustStockUseCase adjustStockUseCase;
    private final QuarantineUseCase quarantineUseCase;
    private final ReleaseFromQuarantineUseCase releaseFromQuarantineUseCase;
    private final WriteOffUseCase writeOffUseCase;
    private final GetStockSummaryUseCase getStockSummaryUseCase;
    private final BatchRepository batchRepository;
    private final MovementRepository movementRepository;
    private final ProductRepository productRepository;

    public BatchController(ReceiveStockUseCase receiveStockUseCase,
                            AdjustStockUseCase adjustStockUseCase,
                            QuarantineUseCase quarantineUseCase,
                            ReleaseFromQuarantineUseCase releaseFromQuarantineUseCase,
                            WriteOffUseCase writeOffUseCase,
                            GetStockSummaryUseCase getStockSummaryUseCase,
                            BatchRepository batchRepository,
                            MovementRepository movementRepository,
                            ProductRepository productRepository) {
        this.receiveStockUseCase = receiveStockUseCase;
        this.adjustStockUseCase = adjustStockUseCase;
        this.quarantineUseCase = quarantineUseCase;
        this.releaseFromQuarantineUseCase = releaseFromQuarantineUseCase;
        this.writeOffUseCase = writeOffUseCase;
        this.getStockSummaryUseCase = getStockSummaryUseCase;
        this.batchRepository = batchRepository;
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
    }

    @PostMapping
    public ResponseEntity<BatchResponse> receive(@Valid @RequestBody CreateBatchRequest request) {
        Batch batch = receiveStockUseCase.execute(
                request.productId(), request.batchCode(), request.quantity(),
                request.expirationDate(), request.costPerUnit(),
                request.manufacturingDate(), request.category(), request.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(BatchResponse.fromDomain(batch));
    }

    @GetMapping
    public ResponseEntity<List<BatchResponse>> list() {
        String tenantId = TenantContext.getTenantId();
        List<Batch> batches = tenantId != null
                ? batchRepository.findAllByTenantId(tenantId)
                : batchRepository.findAll();

        Map<UUID, String> productNames = batches.stream()
                .map(Batch::getProductId)
                .distinct()
                .collect(Collectors.toMap(
                        pid -> pid,
                        pid -> productRepository.findById(pid)
                                .map(Product::getName)
                                .orElse("Unknown"),
                        (a, b) -> a
                ));

        List<BatchResponse> response = batches.stream()
                .map(b -> BatchResponse.fromDomain(b, productNames.getOrDefault(b.getProductId(), "Unknown")))
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BatchDetailResponse> getById(@PathVariable UUID id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new InventoryDomainException("Batch not found: " + id));
        List<MovementResponse> movements = movementRepository.findByBatchId(id).stream()
                .map(MovementResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(new BatchDetailResponse(BatchResponse.fromDomain(batch), movements));
    }

    @PostMapping("/{id}/quarantine")
    public ResponseEntity<BatchResponse> quarantine(@PathVariable UUID id,
                                                     @Valid @RequestBody QuarantineRequest request) {
        Batch batch = quarantineUseCase.execute(id, request.reason());
        return ResponseEntity.ok(BatchResponse.fromDomain(batch));
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<BatchResponse> releaseFromQuarantine(@PathVariable UUID id,
                                                                @Valid @RequestBody QuarantineRequest request) {
        Batch batch = releaseFromQuarantineUseCase.execute(id, request.reason());
        return ResponseEntity.ok(BatchResponse.fromDomain(batch));
    }

    @PostMapping("/{id}/dispose")
    public ResponseEntity<BatchResponse> dispose(@PathVariable UUID id,
                                                  @Valid @RequestBody DisposeRequest request) {
        Batch batch = writeOffUseCase.execute(id, request.reason());
        return ResponseEntity.ok(BatchResponse.fromDomain(batch));
    }

    @PostMapping("/{id}/adjust")
    public ResponseEntity<BatchResponse> adjust(@PathVariable UUID id,
                                                 @Valid @RequestBody AdjustStockRequest request) {
        Batch batch = adjustStockUseCase.execute(id, request.newQuantity(), request.reason());
        return ResponseEntity.ok(BatchResponse.fromDomain(batch));
    }

    @GetMapping("/stock/{productId}")
    public ResponseEntity<StockProjectionResponse> stockSummary(@PathVariable UUID productId) {
        StockProjection projection = getStockSummaryUseCase.execute(productId);
        return ResponseEntity.ok(StockProjectionResponse.fromDomain(projection));
    }
}
