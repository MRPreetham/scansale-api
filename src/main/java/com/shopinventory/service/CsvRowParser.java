package com.shopinventory.service;

import com.shopinventory.web.dto.Dtos.ColumnMapping;
import com.shopinventory.web.dto.Dtos.RowError;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure CSV column-mapping and row-parsing logic, shared by the import flow.
 */
public final class CsvRowParser {

    public record CsvRow(int row, String barcode, String name, String unit, BigDecimal price, BigDecimal qty) {
    }

    public record Outcome(List<CsvRow> rows, List<RowError> errors) {
    }

    private static final Pattern CARTON_PATTERN =
            Pattern.compile("x(\\d+)\\s*(?:p\\.?)?\\s*btls?", Pattern.CASE_INSENSITIVE);

    private CsvRowParser() {
    }

    /**
     * Resolves the index of each system column from a user-provided mapping. A field whose
     * mapped column is absent (or blank) resolves to {@code null}. The "barcode" field is
     * required — the caller must reject a null barcode column.
     */
    public static Map<String, Integer> resolve(String[] header, ColumnMapping mapping) {
        Map<String, Integer> byName = indexByName(header);
        Map<String, Integer> cols = new HashMap<>();
        cols.put("barcode", mapping.barcode() == null ? null : byName.get(normalize(mapping.barcode())));
        cols.put("name", mapping.name() == null ? null : byName.get(normalize(mapping.name())));
        cols.put("unit", mapping.unit() == null ? null : byName.get(normalize(mapping.unit())));
        cols.put("price", mapping.price() == null ? null : byName.get(normalize(mapping.price())));
        cols.put("qty", mapping.qty() == null ? null : byName.get(normalize(mapping.qty())));
        return cols;
    }

    /**
     * Legacy auto-detection: matches common header variants (case-insensitive, _ and - as spaces).
     */
    public static Map<String, Integer> autoDetect(String[] header) {
        Map<String, Integer> byName = indexByName(header);
        Map<String, Integer> cols = new HashMap<>();
        cols.put("barcode", match(byName, "barcode", "code"));
        cols.put("name", match(byName, "name", "product"));
        cols.put("unit", match(byName, "unit"));
        cols.put("price", match(byName, "price", "rate"));
        cols.put("qty", match(byName, "qty", "quantity", "stock", "opening"));
        return cols;
    }

    /**
     * Parses data rows (raw[0] must be the header row) into normalized rows, collecting
     * per-row errors for missing barcodes and invalid numbers. Blank rows are skipped.
     */
    public static Outcome parseRows(List<String[]> raw, Map<String, Integer> cols) {
        List<CsvRow> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        for (int i = 1; i < raw.size(); i++) {
            String[] values = raw.get(i);
            int rowNumber = i + 1;
            if (isBlank(values)) continue;

            String barcode = cell(values, cols.get("barcode"));
            if (barcode == null) {
                errors.add(new RowError(rowNumber, "Missing barcode"));
                continue;
            }
            BigDecimal price = null;
            BigDecimal qty = null;
            boolean valid = true;
            if (cols.get("price") != null) {
                String rawPrice = cell(values, cols.get("price"));
                if (rawPrice != null) {
                    try {
                        price = new BigDecimal(rawPrice);
                    } catch (NumberFormatException e) {
                        errors.add(new RowError(rowNumber, "Invalid price value '" + rawPrice + "'"));
                        valid = false;
                    }
                }
            }
            if (cols.get("qty") != null) {
                String rawQty = cell(values, cols.get("qty"));
                if (rawQty != null) {
                    try {
                        qty = new BigDecimal(rawQty);
                    } catch (NumberFormatException e) {
                        errors.add(new RowError(rowNumber, "Invalid quantity value '" + rawQty + "'"));
                        valid = false;
                    }
                }
            }
            if (!valid) continue;
            rows.add(new CsvRow(rowNumber, barcode, cell(values, cols.get("name")),
                    cell(values, cols.get("unit")), price, qty));
        }
        return new Outcome(rows, errors);
    }

    /**
     * Extracts the bottles-per-carton count from a product name, e.g. "180MLx48Btls" → 48,
     * "750MLx12P.Btls" → 12. Returns 0 when no count can be detected.
     */
    public static int bottlesPerCarton(String name) {
        if (name == null) return 0;
        Matcher m = CARTON_PATTERN.matcher(name);
        if (!m.find()) return 0;
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Carton pricing mode: each row's unit price becomes cartonPrice / bottlesPerCarton and the
     * quantity becomes cartons × bottlesPerCarton. Rows with no detectable count are flagged.
     */
    public static Outcome applyCartonPricing(Outcome outcome) {
        List<CsvRow> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>(outcome.errors());
        for (CsvRow row : outcome.rows()) {
            int pieces = bottlesPerCarton(row.name());
            if (pieces <= 0) {
                errors.add(new RowError(row.row(), "Could not detect bottles per carton in name"));
                continue;
            }
            BigDecimal unitPrice = row.price() == null
                    ? null
                    : row.price().divide(BigDecimal.valueOf(pieces), 2, RoundingMode.HALF_UP);
            BigDecimal totalQty = row.qty() == null
                    ? null
                    : row.qty().multiply(BigDecimal.valueOf(pieces));
            rows.add(new CsvRow(row.row(), row.barcode(), row.name(), row.unit(), unitPrice, totalQty));
        }
        return new Outcome(rows, errors);
    }

    private static Map<String, Integer> indexByName(String[] header) {        Map<String, Integer> byName = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            byName.put(normalize(header[i]), i);
        }
        return byName;
    }

    private static Integer match(Map<String, Integer> byName, String... needles) {
        for (String needle : needles) {
            for (Map.Entry<String, Integer> entry : byName.entrySet()) {
                if (entry.getKey().contains(needle)) return entry.getValue();
            }
        }
        return null;
    }

    private static String cell(String[] values, Integer col) {
        if (col == null || col >= values.length) return null;
        String value = values[col];
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String[] values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return false;
        }
        return true;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase().replace("_", " ").replace("-", " ").replaceAll("\\s+", " ");
    }
}
