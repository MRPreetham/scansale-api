package com.shopinventory.service;

import com.shopinventory.domain.organization.Organization;
import com.shopinventory.domain.organization.OrganizationRepository;
import com.shopinventory.domain.organization.OrgSettings;
import com.shopinventory.domain.organization.OrgSettingsRepository;
import com.shopinventory.domain.product.Product;
import com.shopinventory.domain.product.ProductRepository;
import com.shopinventory.domain.sale.Sale;
import com.shopinventory.domain.sale.SaleLine;
import com.shopinventory.domain.sale.SaleRepository;
import com.shopinventory.domain.sale.SaleStatus;
import com.shopinventory.domain.stock.MovementType;
import com.shopinventory.domain.stock.StockMovement;
import com.shopinventory.domain.stock.StockMovementRepository;
import com.shopinventory.domain.user.User;
import com.shopinventory.domain.user.UserRepository;
import com.shopinventory.security.AppPrincipal;
import com.shopinventory.web.ApiException;
import com.shopinventory.web.dto.Dtos.SaleLineResponse;
import com.shopinventory.web.dto.Dtos.SaleRequest;
import com.shopinventory.web.dto.Dtos.SalePageResponse;
import com.shopinventory.web.dto.Dtos.SaleResponse;
import com.shopinventory.web.dto.Dtos.ShopDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrgSettingsRepository orgSettingsRepository;
    private final AuditService auditService;

    public SaleService(SaleRepository saleRepository, ProductRepository productRepository,
                       StockMovementRepository stockMovementRepository, UserRepository userRepository,
                       OrganizationRepository organizationRepository, OrgSettingsRepository orgSettingsRepository,
                       AuditService auditService) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.orgSettingsRepository = orgSettingsRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public SalePageResponse list(UUID orgId, int page, int size) {
        org.springframework.data.domain.Page<Sale> result = saleRepository
                .findByOrgIdOrderBySoldAtDesc(orgId, org.springframework.data.domain.PageRequest.of(page, size));
        return new SalePageResponse(result.stream().map(this::toResponse).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public SaleResponse get(UUID orgId, UUID id) {
        Sale sale = saleRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> ApiException.notFound("Sale not found"));
        return toResponse(sale);
    }

    @Transactional
    public SaleResponse create(UUID orgId, AppPrincipal principal, SaleRequest request) {
        if (request.lines() == null || request.lines().isEmpty()) {
            throw ApiException.badRequest("Sale must contain at least one line");
        }

        Map<UUID, BigDecimal> merged = new LinkedHashMap<>();
        Map<UUID, Product> products = new LinkedHashMap<>();
        for (var line : request.lines()) {
            if (line.barcode() == null || line.barcode().isBlank()) {
                throw ApiException.badRequest("A line is missing a barcode");
            }
            Product product = productRepository.findForUpdateByBarcode(orgId, line.barcode().trim())
                    .orElseThrow(() -> ApiException.notFound("No product with barcode '" + line.barcode() + "'"));
            BigDecimal qty = line.qty() == null ? BigDecimal.ONE : line.qty();
            if (qty.signum() <= 0) {
                throw ApiException.badRequest("Quantity must be greater than zero for '" + product.getName() + "'");
            }
            merged.merge(product.getId(), qty, BigDecimal::add);
            products.put(product.getId(), product);
        }

        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        var lineData = new ArrayList<Object[]>();
        for (var entry : merged.entrySet()) {
            Product product = products.get(entry.getKey());
            BigDecimal qty = entry.getValue();
            if (product.getAvailableQty().compareTo(qty) < 0) {
                throw ApiException.conflict("Only " + product.getAvailableQty().stripTrailingZeros().toPlainString()
                        + " available for '" + product.getName() + "'");
            }
            BigDecimal unitPrice = product.getSellingPrice();
            BigDecimal amount = unitPrice.multiply(qty);
            totalQty = totalQty.add(qty);
            totalAmount = totalAmount.add(amount);
            lineData.add(new Object[]{product, qty, unitPrice, amount});
        }

        User cashier = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.forbidden("User not found"));

        int year = Year.now().getValue();
        Integer maxSeq = saleRepository.maxNumberSeq(orgId, year);
        int seq = (maxSeq == null ? 0 : maxSeq) + 1;

        Sale sale = new Sale();
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> ApiException.forbidden("Organization not found"));
        sale.setOrg(org);
        sale.setYear(year);
        sale.setNumberSeq(seq);
        sale.setSaleNumber(String.format("SLS-%04d/%d", seq, year));
        sale.setCashier(cashier);
        sale.setPaymentMode(request.paymentMode() == null
                ? com.shopinventory.domain.sale.PaymentMode.CASH : request.paymentMode());
        sale.setStatus(SaleStatus.SUBMITTED);
        sale.setTotalQty(totalQty);
        sale.setTotalAmount(totalAmount);
        sale.setNotes(request.notes());

        List<SaleLine> lines = new ArrayList<>();
        for (Object[] data : lineData) {
            Product product = (Product) data[0];
            BigDecimal qty = (BigDecimal) data[1];
            BigDecimal unitPrice = (BigDecimal) data[2];
            BigDecimal amount = (BigDecimal) data[3];

            SaleLine line = new SaleLine();
            line.setSale(sale);
            line.setProduct(product);
            line.setBarcode(product.getBarcode());
            line.setName(product.getName());
            line.setUnit(product.getUnit());
            line.setSize(product.getSize());
            line.setQty(qty);
            line.setUnitPrice(unitPrice);
            line.setAmount(amount);
            lines.add(line);

            product.setAvailableQty(product.getAvailableQty().subtract(qty));
            productRepository.save(product);
            stockMovementRepository.save(movement(orgId, product, MovementType.SALE,
                    qty.negate(), principal.userId(), null, sale));
        }
        sale.setLines(lines);
        Sale saved = saleRepository.save(sale);

        auditService.log(orgId, cashier, "SALE_CREATE", "Sale", saved.getId().toString(),
                Map.of("saleNumber", saved.getSaleNumber(), "totalAmount", totalAmount));
        return toResponse(saved);
    }

    private StockMovement movement(UUID orgId, Product product, MovementType type,
                                   BigDecimal qtyDelta, UUID actorId, UUID refImportId, Sale refSale) {
        StockMovement movement = new StockMovement();
        Organization org = new Organization();
        org.setId(orgId);
        movement.setOrg(org);
        movement.setProduct(product);
        movement.setType(type);
        movement.setQtyDelta(qtyDelta);
        movement.setRefSale(refSale);
        User actor = new User();
        actor.setId(actorId);
        movement.setCreatedBy(actor);
        return movement;
    }

    private SaleResponse toResponse(Sale sale) {
        List<SaleLineResponse> lines = sale.getLines().stream()
                .map(l -> new SaleLineResponse(l.getProduct().getId(), l.getBarcode(), l.getName(),
                        l.getQty(), l.getUnitPrice(), l.getAmount(), l.getUnit(), l.getSize()))
                .toList();
        Organization org = sale.getOrg();
        OrgSettings s = orgSettingsRepository.findByOrgId(org.getId()).orElse(null);
        ShopDetails shop = new ShopDetails(org.getName(),
                s == null ? null : s.getAddress(),
                s == null ? null : s.getPhone(),
                s == null ? null : s.getEmail(),
                s == null ? null : s.getGstin(),
                org.getCurrency());
        return new SaleResponse(sale.getId(), sale.getSaleNumber(), sale.getSoldAt(),
                sale.getCashier().getName(), sale.getPaymentMode(),
                sale.getStatus().name(), sale.getTotalQty(), sale.getTotalAmount(), lines, shop);
    }
}