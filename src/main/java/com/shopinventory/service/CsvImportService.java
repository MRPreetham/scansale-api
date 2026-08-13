package com.shopinventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.shopinventory.domain.organization.Organization;
import com.shopinventory.domain.organization.OrganizationRepository;
import com.shopinventory.domain.product.Product;
import com.shopinventory.domain.product.ProductRepository;
import com.shopinventory.domain.stock.ImportStatus;
import com.shopinventory.domain.stock.MovementType;
import com.shopinventory.domain.stock.StockImport;
import com.shopinventory.domain.stock.StockImportRepository;
import com.shopinventory.domain.stock.StockMovement;
import com.shopinventory.domain.stock.StockMovementRepository;
import com.shopinventory.domain.user.User;
import com.shopinventory.domain.user.UserRepository;
import com.shopinventory.security.AppPrincipal;
import com.shopinventory.web.ApiException;
import com.shopinventory.web.dto.Dtos.ImportCommitResponse;
import com.shopinventory.web.dto.Dtos.ImportHistoryPageResponse;
import com.shopinventory.web.dto.Dtos.ImportHistoryResponse;
import com.shopinventory.web.dto.Dtos.ImportPreviewResponse;
import com.shopinventory.web.dto.Dtos.RowError;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CsvImportService {

    private record CsvRow(int row, String barcode, String name, String unit, BigDecimal price, BigDecimal qty) {
    }

    private final StockImportRepository stockImportRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public CsvImportService(StockImportRepository stockImportRepository,
                            ProductRepository productRepository,
                            StockMovementRepository stockMovementRepository,
                            OrganizationRepository organizationRepository,
                            UserRepository userRepository,
                            AuditService auditService,
                            ObjectMapper objectMapper) {
        this.stockImportRepository = stockImportRepository;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImportPreviewResponse preview(UUID orgId, AppPrincipal principal, MultipartFile file) {
        List<CsvRow> rows;
        List<RowError> errors = new ArrayList<>();
        try {
            rows = parse(file, errors);
        } catch (Exception e) {
            throw ApiException.badRequest("Could not read CSV: " + e.getMessage());
        }

        if (errors.isEmpty() && rows.isEmpty()) {
            throw ApiException.badRequest("CSV contains no data rows");
        }

        Map<String, Product> existing = productsByBarcode(orgId);
        int newCount = 0;
        int updateCount = 0;
        int skipCount = errors.size();
        for (CsvRow row : rows) {
            if (existing.containsKey(normalize(row.barcode()))) {
                updateCount++;
            } else {
                newCount++;
            }
        }

        StockImport stockImport = new StockImport();
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> ApiException.forbidden("Organization not found"));
        stockImport.setOrg(org);
        stockImport.setImportedBy(userRepository.findById(principal.userId()).orElseThrow());
        stockImport.setFilename(file.getOriginalFilename() == null ? file.getName() : file.getOriginalFilename());
        stockImport.setStatus(ImportStatus.PREVIEW);
        stockImport.setNewCount(newCount);
        stockImport.setUpdateCount(updateCount);
        stockImport.setSkipCount(skipCount);
        stockImport.setSummaryJson(toJson(rows));
        stockImportRepository.save(stockImport);

        auditService.log(orgId, userRepository.findById(principal.userId()).orElseThrow(),
                "IMPORT_PREVIEW", "StockImport", stockImport.getId().toString(),
                Map.of("filename", stockImport.getFilename(),
                        "new", newCount, "update", updateCount, "skip", skipCount));

        return new ImportPreviewResponse(stockImport.getId(), stockImport.getFilename(),
                newCount, updateCount, skipCount, errors);
    }

    @Transactional
    public ImportCommitResponse commit(UUID orgId, AppPrincipal principal, UUID importId) {
        StockImport stockImport = stockImportRepository.findByOrgIdAndId(orgId, importId)
                .orElseThrow(() -> ApiException.notFound("Import not found"));
        if (stockImport.getStatus() != ImportStatus.PREVIEW) {
            throw ApiException.conflict("Import has already been processed");
        }

        List<CsvRow> rows = fromJson(stockImport.getSummaryJson());
        Map<String, Product> existing = productsByBarcode(orgId);

        int newCount = 0;
        int updateCount = 0;
        int skipCount = 0;

        for (CsvRow row : rows) {
            Product product = existing.get(normalize(row.barcode()));
            try {
                if (product == null) {
                    product = new Product();
                    Organization orgRef = new Organization();
                    orgRef.setId(orgId);
                    product.setOrg(orgRef);
                    product.setName(row.name() == null ? row.barcode() : row.name());
                    product.setBarcode(normalize(row.barcode()));
                    product.setUnit(row.unit() == null ? "pcs" : row.unit());
                    product.setSellingPrice(row.price() == null ? BigDecimal.ZERO : row.price());
                    BigDecimal opening = row.qty() == null ? BigDecimal.ZERO : row.qty();
                    product.setOpeningQty(opening);
                    product.setAvailableQty(opening);
                    productRepository.save(product);
                    newCount++;
                    if (opening.signum() > 0) {
                        stockMovementRepository.save(movement(orgId, product, MovementType.OPENING,
                                opening, principal.userId(), stockImport));
                    }
                } else {
                    if (row.name() != null) product.setName(row.name());
                    if (row.unit() != null) product.setUnit(row.unit());
                    if (row.price() != null) product.setSellingPrice(row.price());
                    if (row.qty() != null && row.qty().signum() != 0) {
                        product.setAvailableQty(product.getAvailableQty().add(row.qty()));
                        stockMovementRepository.save(movement(orgId, product, MovementType.IMPORT,
                                row.qty(), principal.userId(), stockImport));
                    }
                    productRepository.save(product);
                    updateCount++;
                }
            } catch (Exception e) {
                skipCount++;
            }
        }

        stockImport.setStatus(ImportStatus.COMMITTED);
        stockImport.setNewCount(newCount);
        stockImport.setUpdateCount(updateCount);
        stockImport.setSkipCount(skipCount);
        stockImportRepository.save(stockImport);

        auditService.log(orgId, userRepository.findById(principal.userId()).orElseThrow(),
                "IMPORT_COMMIT", "StockImport", importId.toString(),
                Map.of("new", newCount, "update", updateCount, "skip", skipCount));
        return new ImportCommitResponse(importId, newCount, updateCount, skipCount);
    }

    @Transactional(readOnly = true)
    public ImportHistoryPageResponse history(UUID orgId, int page, int size) {
        org.springframework.data.domain.Page<StockImport> result = stockImportRepository
                .findByOrgIdOrderByImportedAtDesc(orgId, org.springframework.data.domain.PageRequest.of(page, size));
        List<ImportHistoryResponse> items = result.stream()
                .map(i -> new ImportHistoryResponse(i.getId(), i.getFilename(), i.getStatus().name(),
                        i.getImportedAt(), i.getNewCount(), i.getUpdateCount(), i.getSkipCount()))
                .toList();
        return new ImportHistoryPageResponse(items, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private List<CsvRow> parse(MultipartFile file, List<RowError> errors) throws Exception {
        List<CsvRow> rows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVReader csv = new CSVReader(reader)) {
            String[] header = csv.readNext();
            if (header == null) {
                throw new IllegalArgumentException("Empty file");
            }
            Map<String, Integer> index = columnIndex(header);
            Integer barcodeCol = index.get("barcode");
            if (barcodeCol == null) {
                throw new IllegalArgumentException("Missing required 'barcode' column");
            }
            Integer nameCol = index.get("name");
            Integer unitCol = index.get("unit");
            Integer priceCol = index.get("price");
            Integer qtyCol = index.get("qty");

            String[] values;
            int rowNumber = 1;
            while ((values = csv.readNext()) != null) {
                rowNumber++;
                if (isBlank(values)) continue;

                String barcode = cell(values, barcodeCol);
                if (barcode == null) {
                    errors.add(new RowError(rowNumber, "Missing barcode"));
                    continue;
                }
                BigDecimal price = null;
                BigDecimal qty = null;
                boolean valid = true;
                if (priceCol != null) {
                    String raw = cell(values, priceCol);
                    if (raw != null) {
                        try {
                            price = new BigDecimal(raw);
                        } catch (NumberFormatException e) {
                            errors.add(new RowError(rowNumber, "Invalid price value '" + raw + "'"));
                            valid = false;
                        }
                    }
                }
                if (qtyCol != null) {
                    String raw = cell(values, qtyCol);
                    if (raw != null) {
                        try {
                            qty = new BigDecimal(raw);
                        } catch (NumberFormatException e) {
                            errors.add(new RowError(rowNumber, "Invalid quantity value '" + raw + "'"));
                            valid = false;
                        }
                    }
                }
                if (!valid) continue;
                rows.add(new CsvRow(rowNumber, barcode, cell(values, nameCol), cell(values, unitCol), price, qty));
            }
        }
        return rows;
    }

    private Map<String, Integer> columnIndex(String[] header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String raw = header[i] == null ? "" : header[i].trim().toLowerCase()
                    .replace("_", " ").replace("-", " ").replaceAll("\\s+", " ");
            if (raw.contains("barcode") || raw.contains("code")) index.putIfAbsent("barcode", i);
            else if (raw.contains("name") || raw.contains("product")) index.putIfAbsent("name", i);
            else if (raw.contains("unit")) index.putIfAbsent("unit", i);
            else if (raw.contains("price") || raw.contains("rate")) index.putIfAbsent("price", i);
            else if (raw.contains("qty") || raw.contains("quantity") || raw.contains("stock") || raw.contains("opening")) index.putIfAbsent("qty", i);
        }
        return index;
    }

    private String cell(String[] values, Integer col) {
        if (col == null || col >= values.length) return null;
        String value = values[col];
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String[] values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return false;
        }
        return true;
    }

    private Map<String, Product> productsByBarcode(UUID orgId) {
        Map<String, Product> map = new HashMap<>();
        for (Product p : productRepository.findAllByOrgIdOrderByNameAsc(orgId)) {
            map.put(normalize(p.getBarcode()), p);
        }
        return map;
    }

    private StockMovement movement(UUID orgId, Product product, MovementType type,
                                   BigDecimal qtyDelta, UUID actorId, StockImport refImport) {
        StockMovement movement = new StockMovement();
        Organization org = new Organization();
        org.setId(orgId);
        movement.setOrg(org);
        movement.setProduct(product);
        movement.setType(type);
        movement.setQtyDelta(qtyDelta);
        movement.setRefImport(refImport);
        User actor = new User();
        actor.setId(actorId);
        movement.setCreatedBy(actor);
        return movement;
    }

    private String toJson(List<CsvRow> rows) {
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize import rows", e);
        }
    }

    private List<CsvRow> fromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<CsvRow>>() {
            });
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Stored import data is unreadable");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}