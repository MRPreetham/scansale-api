package com.shopinventory.service;

import com.shopinventory.domain.product.Product;
import com.shopinventory.domain.product.ProductRepository;
import com.shopinventory.domain.sale.PaymentMode;
import com.shopinventory.domain.sale.SaleRepository;
import com.shopinventory.domain.stock.StockMovementRepository;
import com.shopinventory.web.ApiException;
import com.shopinventory.web.dto.Dtos.DailyReportResponse;
import com.shopinventory.web.dto.Dtos.DailyRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final SaleRepository saleRepository;

    public ReportService(ProductRepository productRepository,
                         StockMovementRepository stockMovementRepository,
                         SaleRepository saleRepository) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.saleRepository = saleRepository;
    }

    @Transactional(readOnly = true)
    public DailyReportResponse daily(UUID orgId, String dateParam) {
        LocalDate date;
        try {
            date = LocalDate.parse(dateParam);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("date must be in yyyy-MM-dd format");
        }
        ZoneId zone = ZoneId.systemDefault();
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();

        List<Product> products = productRepository.findAllByOrgIdOrderByNameAsc(orgId);

        Map<UUID, BigDecimal> since = toMap(stockMovementRepository.sumSince(orgId, from));
        Map<UUID, BigDecimal> netDay = toMap(stockMovementRepository.sumBetween(orgId, from, to));
        Map<UUID, BigDecimal> placed = toMap(stockMovementRepository.placedBetween(orgId, from, to));
        Map<UUID, BigDecimal> sold = toMap(stockMovementRepository.soldBetween(orgId, from, to));

        List<DailyRow> rows = new ArrayList<>();
        BigDecimal totalUnitsSold = BigDecimal.ZERO;
        for (Product product : products) {
            UUID pid = product.getId();
            BigDecimal sinceSum = since.getOrDefault(pid, BigDecimal.ZERO);
            BigDecimal openingQty = product.getAvailableQty().subtract(sinceSum);
            BigDecimal net = netDay.getOrDefault(pid, BigDecimal.ZERO);
            BigDecimal placedQty = placed.getOrDefault(pid, BigDecimal.ZERO);
            BigDecimal soldQty = sold.getOrDefault(pid, BigDecimal.ZERO);
            BigDecimal endQty = openingQty.add(net);

            boolean active = netDay.containsKey(pid) || sold.containsKey(pid);
            if (!active) continue;

            rows.add(new DailyRow(pid, product.getSku(), product.getName(), product.getBarcode(),
                    openingQty, placedQty, soldQty, endQty,
                    product.getReorderLevel(), endQty.compareTo(product.getReorderLevel()) <= 0));
            totalUnitsSold = totalUnitsSold.add(soldQty);
        }

        BigDecimal totalSalesAmount = saleRepository.sumAmountBetween(orgId, from, to);
        Map<String, BigDecimal> paymentBreakdown = new LinkedHashMap<>();
        for (Object[] row : saleRepository.paymentModeBreakdown(orgId, from, to)) {
            paymentBreakdown.put(((PaymentMode) row[0]).name(), (BigDecimal) row[1]);
        }

        return new DailyReportResponse(date.toString(), totalSalesAmount, totalUnitsSold,
                paymentBreakdown, rows);
    }

    private Map<UUID, BigDecimal> toMap(List<Object[]> rows) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        for (Object[] row : rows) {
            BigDecimal value = (BigDecimal) row[1];
            map.put((UUID) row[0], value == null ? BigDecimal.ZERO : value);
        }
        return map;
    }
}