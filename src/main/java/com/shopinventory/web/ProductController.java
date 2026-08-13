package com.shopinventory.web;

import com.shopinventory.security.AppPrincipal;
import com.shopinventory.security.Capabilities;
import com.shopinventory.service.ProductService;
import com.shopinventory.web.dto.Dtos.AdjustStockRequest;
import com.shopinventory.web.dto.Dtos.ProductPageResponse;
import com.shopinventory.web.dto.Dtos.ProductRequest;
import com.shopinventory.web.dto.Dtos.ProductResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ProductPageResponse list(@AuthenticationPrincipal AppPrincipal principal,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) Boolean low,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return productService.list(principal.orgId(), q, low, page, size);
    }

    @GetMapping("/by-barcode/{barcode}")
    public ProductResponse getByBarcode(@AuthenticationPrincipal AppPrincipal principal, @PathVariable String barcode) {
        return productService.getByBarcode(principal.orgId(), barcode);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@AuthenticationPrincipal AppPrincipal principal, @PathVariable UUID id) {
        return productService.get(principal.orgId(), id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Capabilities.PRODUCT_WRITE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@AuthenticationPrincipal AppPrincipal principal,
                                  @Valid @RequestBody ProductRequest request) {
        return productService.create(principal.orgId(), principal, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Capabilities.PRODUCT_WRITE + "')")
    public ProductResponse update(@AuthenticationPrincipal AppPrincipal principal,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody ProductRequest request) {
        return productService.update(principal.orgId(), id, principal, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Capabilities.PRODUCT_DELETE + "')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppPrincipal principal, @PathVariable UUID id) {
        productService.delete(principal.orgId(), id, principal);
    }

    @PostMapping("/{id}/stock/adjust")
    @PreAuthorize("hasAuthority('" + Capabilities.STOCK_ADJUST + "')")
    public ProductResponse adjustStock(@AuthenticationPrincipal AppPrincipal principal,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody AdjustStockRequest request) {
        return productService.adjustStock(principal.orgId(), id, principal, request);
    }
}