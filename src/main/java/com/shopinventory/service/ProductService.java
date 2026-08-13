package com.shopinventory.service;

import com.shopinventory.domain.organization.Organization;
import com.shopinventory.domain.organization.OrganizationRepository;
import com.shopinventory.domain.product.Product;
import com.shopinventory.domain.product.ProductRepository;
import com.shopinventory.domain.stock.MovementType;
import com.shopinventory.domain.stock.StockMovement;
import com.shopinventory.domain.stock.StockMovementRepository;
import com.shopinventory.domain.user.User;
import com.shopinventory.domain.user.UserRepository;
import com.shopinventory.security.AppPrincipal;
import com.shopinventory.web.ApiException;
import com.shopinventory.web.dto.Dtos.AdjustStockRequest;
import com.shopinventory.web.dto.Dtos.ProductPageResponse;
import com.shopinventory.web.dto.Dtos.ProductRequest;
import com.shopinventory.web.dto.Dtos.ProductResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final OrganizationRepository organizationRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ProductService(ProductRepository productRepository,
                          OrganizationRepository organizationRepository,
                          StockMovementRepository stockMovementRepository,
                          UserRepository userRepository,
                          AuditService auditService) {
        this.productRepository = productRepository;
        this.organizationRepository = organizationRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ProductPageResponse list(UUID orgId, String q, Boolean lowOnly, int page, int size) {
        String query = q == null ? "" : q.trim();
        boolean low = lowOnly != null && lowOnly;
        org.springframework.data.domain.Page<Product> result =
                productRepository.searchPage(orgId, query, low,
                        org.springframework.data.domain.PageRequest.of(page, size));
        return new ProductPageResponse(
                result.stream().map(this::toResponse).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize());
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID orgId, UUID id) {
        return toResponse(find(orgId, id));
    }

    @Transactional(readOnly = true)
    public ProductResponse getByBarcode(UUID orgId, String barcode) {
        return toResponse(productRepository.findByOrgIdAndBarcode(orgId, barcode)
                .orElseThrow(() -> ApiException.notFound("No product with barcode '" + barcode + "'")));
    }

    @Transactional
    public ProductResponse create(UUID orgId, AppPrincipal principal, ProductRequest request) {
        if (productRepository.findByOrgIdAndBarcode(orgId, request.barcode().trim()).isPresent()) {
            throw ApiException.conflict("A product with barcode '" + request.barcode() + "' already exists");
        }

        Product product = new Product();
        applyFields(product, request, false);

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> ApiException.forbidden("Organization not found"));
        product.setOrg(org);

        BigDecimal opening = request.openingQty() == null ? BigDecimal.ZERO : request.openingQty();
        if (opening.signum() < 0) {
            throw ApiException.badRequest("Opening quantity cannot be negative");
        }
        product.setOpeningQty(opening);
        product.setAvailableQty(opening);
        Product saved = productRepository.save(product);

        if (opening.signum() > 0) {
            stockMovementRepository.save(movement(orgId, saved, MovementType.OPENING, opening, principal.userId(), null));
        }

        auditService.log(orgId, userRepository.findById(principal.userId()).orElseThrow(),
                "PRODUCT_CREATE", "Product", saved.getId().toString(),
                Map.of("name", saved.getName(), "barcode", saved.getBarcode()));
        return toResponse(saved);
    }

    @Transactional
    public ProductResponse update(UUID orgId, UUID id, AppPrincipal principal, ProductRequest request) {
        Product product = findForUpdate(orgId, id);
        if (request.barcode() != null && !request.barcode().isBlank()) {
            String barcode = request.barcode().trim();
            productRepository.findByOrgIdAndBarcode(orgId, barcode).ifPresent(existing -> {
                if (!existing.getId().equals(product.getId())) {
                    throw ApiException.conflict("A product with barcode '" + barcode + "' already exists");
                }
            });
        }
        applyFields(product, request, true);
        Product saved = productRepository.save(product);
        auditService.log(orgId, userRepository.findById(principal.userId()).orElseThrow(),
                "PRODUCT_UPDATE", "Product", saved.getId().toString(),
                Map.of("name", saved.getName(), "barcode", saved.getBarcode()));
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID orgId, UUID id, AppPrincipal principal) {
        Product product = find(orgId, id);
        if (stockMovementRepository.existsByOrgIdAndProductId(orgId, id)) {
            throw ApiException.conflict("Product has stock movements and cannot be deleted");
        }
        productRepository.delete(product);
        auditService.log(orgId, userRepository.findById(principal.userId()).orElseThrow(),
                "PRODUCT_DELETE", "Product", id.toString(),
                Map.of("name", product.getName(), "barcode", product.getBarcode()));
    }

    @Transactional
    public ProductResponse adjustStock(UUID orgId, UUID productId, AppPrincipal principal, AdjustStockRequest request) {
        if (request.newQuantity() == null) {
            throw ApiException.badRequest("newQuantity is required");
        }
        if (request.newQuantity().signum() < 0) {
            throw ApiException.badRequest("Stock cannot be negative");
        }
        Product product = findForUpdate(orgId, productId);
        BigDecimal oldQuantity = product.getAvailableQty();
        BigDecimal delta = request.newQuantity().subtract(oldQuantity);
        product.setAvailableQty(request.newQuantity());
        productRepository.save(product);

        if (delta.signum() != 0) {
            stockMovementRepository.save(movement(orgId, product, MovementType.ADJUSTMENT, delta, principal.userId(), null));
        }
        auditService.log(orgId, userRepository.findById(principal.userId()).orElseThrow(),
                "STOCK_ADJUST", "Product", product.getId().toString(),
                map("name", product.getName(), "newQuantity", request.newQuantity(),
                        "oldQuantity", oldQuantity, "reason", request.reason()));
        return toResponse(product);
    }

    private void applyFields(Product product, ProductRequest request, boolean partial) {
        if (!partial || request.sku() != null) product.setSku(trimToNull(request.sku()));
        if (!partial || request.name() != null) product.setName(request.name().trim());
        if (!partial || request.barcode() != null) product.setBarcode(request.barcode().trim());
        if (!partial || request.unit() != null) product.setUnit(trimToNull(request.unit()) == null ? "pcs" : request.unit().trim());
        if (!partial || request.sellingPrice() != null) product.setSellingPrice(defaultZero(request.sellingPrice()));
        if (!partial || request.reorderLevel() != null) product.setReorderLevel(defaultZero(request.reorderLevel()));
        if (!partial || request.notes() != null) product.setNotes(trimToNull(request.notes()));
    }

    private Product find(UUID orgId, UUID id) {
        return productRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private Product findForUpdate(UUID orgId, UUID id) {
        return productRepository.findForUpdate(orgId, id)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private StockMovement movement(UUID orgId, Product product, MovementType type,
                                   BigDecimal qtyDelta, UUID actorId, UUID refSaleId) {
        StockMovement movement = new StockMovement();
        Organization org = new Organization();
        org.setId(orgId);
        movement.setOrg(org);
        movement.setProduct(product);
        movement.setType(type);
        movement.setQtyDelta(qtyDelta);
        if (refSaleId != null) {
            com.shopinventory.domain.sale.Sale sale = new com.shopinventory.domain.sale.Sale();
            sale.setId(refSaleId);
            movement.setRefSale(sale);
        }
        User actor = new User();
        actor.setId(actorId);
        movement.setCreatedBy(actor);
        return movement;
    }

    private boolean isLow(Product p) {
        return p.getAvailableQty().compareTo(p.getReorderLevel()) <= 0;
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getBarcode(), p.getUnit(),
                p.getSellingPrice(), p.getOpeningQty(), p.getAvailableQty(),
                p.getReorderLevel(), isLow(p), p.getNotes(), p.getCreatedAt(), p.getUpdatedAt());
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}