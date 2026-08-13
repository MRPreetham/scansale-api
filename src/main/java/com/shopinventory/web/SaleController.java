package com.shopinventory.web;

import com.shopinventory.security.AppPrincipal;
import com.shopinventory.security.Capabilities;
import com.shopinventory.service.SaleService;
import com.shopinventory.web.dto.Dtos.SalePageResponse;
import com.shopinventory.web.dto.Dtos.SaleRequest;
import com.shopinventory.web.dto.Dtos.SaleResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping
    public SalePageResponse list(@AuthenticationPrincipal AppPrincipal principal,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        return saleService.list(principal.orgId(), page, size);
    }

    @GetMapping("/{id}")
    public SaleResponse get(@AuthenticationPrincipal AppPrincipal principal, @PathVariable UUID id) {
        return saleService.get(principal.orgId(), id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Capabilities.SALE_CREATE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse create(@AuthenticationPrincipal AppPrincipal principal,
                               @Valid @RequestBody SaleRequest request) {
        return saleService.create(principal.orgId(), principal, request);
    }
}