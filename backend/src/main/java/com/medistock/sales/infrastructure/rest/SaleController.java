package com.medistock.sales.infrastructure.rest;

import com.medistock.sales.application.service.CreateSaleCommand;
import com.medistock.sales.application.service.CreateSaleUseCase;
import com.medistock.sales.application.service.VoidSaleUseCase;
import com.medistock.sales.domain.model.Sale;
import com.medistock.sales.domain.port.SaleRepository;
import com.medistock.sales.infrastructure.rest.dto.CreateSaleRequest;
import com.medistock.sales.infrastructure.rest.dto.SaleResponse;
import com.medistock.sales.infrastructure.rest.dto.SaleSummaryResponse;
import com.medistock.sales.infrastructure.rest.dto.VoidSaleRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales")
public class SaleController {

    private final CreateSaleUseCase createSaleUseCase;
    private final VoidSaleUseCase voidSaleUseCase;
    private final SaleRepository saleRepository;

    public SaleController(CreateSaleUseCase createSaleUseCase,
                          VoidSaleUseCase voidSaleUseCase,
                          SaleRepository saleRepository) {
        this.createSaleUseCase = createSaleUseCase;
        this.voidSaleUseCase = voidSaleUseCase;
        this.saleRepository = saleRepository;
    }

    @PostMapping
    public ResponseEntity<SaleResponse> createSale(
            @Valid @RequestBody CreateSaleRequest request,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") String userId) {

        List<CreateSaleCommand.CreateSaleItem> items = request.items().stream()
                .map(i -> new CreateSaleCommand.CreateSaleItem(
                        i.productId(), i.productName(), i.productSku(),
                        i.quantity(), i.unitPrice()))
                .toList();

        CreateSaleCommand command = new CreateSaleCommand(tenantId, userId, items);
        Sale sale = createSaleUseCase.execute(command);
        SaleResponse response = SaleResponse.from(sale);

        return ResponseEntity
                .created(URI.create("/api/v1/sales/" + sale.getSaleId()))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<SaleSummaryResponse>> listSales(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt:desc") String sort,
            @RequestHeader("X-Tenant-Id") String tenantId) {

        List<Sale> sales = saleRepository.findByTenantWithFilters(
                tenantId, productId, status, dateFrom, dateTo, page, size, sort);
        List<SaleSummaryResponse> response = sales.stream()
                .map(SaleSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SaleResponse> getSale(@PathVariable UUID id) {
        return saleRepository.findById(id)
                .map(sale -> ResponseEntity.ok(SaleResponse.from(sale)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<SaleResponse> voidSale(
            @PathVariable UUID id,
            @Valid @RequestBody VoidSaleRequest request) {
        Sale sale = voidSaleUseCase.execute(id, request.reason());
        return ResponseEntity.ok(SaleResponse.from(sale));
    }
}
