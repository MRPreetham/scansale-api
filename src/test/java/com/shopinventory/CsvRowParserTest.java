package com.shopinventory;

import com.shopinventory.service.CsvRowParser;
import com.shopinventory.service.CsvRowParser.Outcome;
import com.shopinventory.web.dto.Dtos.ColumnMapping;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvRowParserTest {
    @Test
    void resolvesColumnsFromUserMapping() {
        String[] header = {"Item Code", "Product Name", "Rate", "Stock Qty", "Notes"};
        Map<String, Integer> cols = CsvRowParser.resolve(header,
                new ColumnMapping("Item Code", "Product Name", null, "Rate", "Stock Qty"));
        assertEquals(0, cols.get("barcode"));
        assertEquals(1, cols.get("name"));
        assertNull(cols.get("unit"));
        assertEquals(2, cols.get("price"));
        assertEquals(3, cols.get("qty"));
    }

    @Test
    void unmappedFieldsResolveToNull() {
        String[] header = {"Code", "Name"};
        Map<String, Integer> cols = CsvRowParser.resolve(header,
                new ColumnMapping("Code", "Name", null, "Rate", null));
        assertNull(cols.get("price"));
        assertNull(cols.get("qty"));
        assertNull(cols.get("unit"));
    }

    @Test
    void parseRowsMapsCustomColumns() {
        String[] header = {"Item Code", "Product Name", "Rate", "Stock Qty"};
        Map<String, Integer> cols = CsvRowParser.resolve(header,
                new ColumnMapping("Item Code", "Product Name", null, "Rate", "Stock Qty"));
        List<String[]> raw = List.of(
                header,
                new String[]{"111", "Coke", "40", "10"},
                new String[]{"222", "Fanta", "35", "5"});
        Outcome outcome = CsvRowParser.parseRows(raw, cols);
        assertEquals(0, outcome.errors().size());
        assertEquals(2, outcome.rows().size());
        assertEquals("111", outcome.rows().get(0).barcode());
        assertEquals("Coke", outcome.rows().get(0).name());
        assertEquals(new BigDecimal("40"), outcome.rows().get(0).price());
        assertEquals(new BigDecimal("10"), outcome.rows().get(0).qty());
    }

    @Test
    void rowWithoutBarcodeProducesError() {
        String[] header = {"Code", "Name"};
        Map<String, Integer> cols = CsvRowParser.resolve(header,
                new ColumnMapping("Code", "Name", null, null, null));
        List<String[]> raw = List.of(header, new String[]{"", "NoBarcode"});
        Outcome outcome = CsvRowParser.parseRows(raw, cols);
        assertEquals(0, outcome.rows().size());
        assertEquals(1, outcome.errors().size());
        assertEquals(2, outcome.errors().get(0).row());
    }

    @Test
    void invalidPriceProducesRowError() {
        String[] header = {"Code", "Name", "Rate"};
        Map<String, Integer> cols = CsvRowParser.resolve(header,
                new ColumnMapping("Code", "Name", null, "Rate", null));
        List<String[]> raw = List.of(header, new String[]{"111", "Coke", "abc"});
        Outcome outcome = CsvRowParser.parseRows(raw, cols);
        assertEquals(1, outcome.errors().size());
        assertEquals(2, outcome.errors().get(0).row());
    }

    @Test
    void blankRowsAreSkipped() {
        String[] header = {"Code", "Name"};
        Map<String, Integer> cols = CsvRowParser.resolve(header,
                new ColumnMapping("Code", "Name", null, null, null));
        List<String[]> raw = List.of(header, new String[]{"", ""}, new String[]{"111", "Coke"});
        Outcome outcome = CsvRowParser.parseRows(raw, cols);
        assertEquals(1, outcome.rows().size());
        assertEquals(0, outcome.errors().size());
    }

    @Test
    void autoDetectRecognizesCommonHeaderVariants() {
        String[] header = {"Barcode", "Product Name", "Unit", "Rate", "Stock"};
        Map<String, Integer> cols = CsvRowParser.autoDetect(header);
        assertEquals(0, cols.get("barcode"));
        assertEquals(1, cols.get("name"));
        assertEquals(2, cols.get("unit"));
        assertEquals(3, cols.get("price"));
        assertEquals(4, cols.get("qty"));
    }

    @Test
    void autoDetectReturnsNullForMissingColumns() {
        String[] header = {"Name"};
        Map<String, Integer> cols = CsvRowParser.autoDetect(header);
        assertNull(cols.get("barcode"));
        assertTrue(cols.containsKey("name"));
    }

    @Test
    void bottlesPerCartonParsesCountFromName() {
        assertEquals(48, CsvRowParser.bottlesPerCarton("Buzzballz 180MLx48Btls(0609)"));
        assertEquals(12, CsvRowParser.bottlesPerCarton("Goana's 750MLx12P.Btls(0609)"));
        assertEquals(9, CsvRowParser.bottlesPerCarton("Old Admiral 1000MLx9Btls(0502)"));
    }

    @Test
    void bottlesPerCartonReturnsZeroWhenNoCountInName() {
        assertEquals(0, CsvRowParser.bottlesPerCarton("Paul John 750ML(0139)"));
        assertEquals(0, CsvRowParser.bottlesPerCarton(null));
        assertEquals(0, CsvRowParser.bottlesPerCarton("Plain Bottle"));
    }

    @Test
    void applyCartonPricingDividesPriceAndMultipliesQty() {
        Map<String, Integer> cols = CsvRowParser.resolve(new String[]{"Code", "Name", "Rate", "Qty"},
                new com.shopinventory.web.dto.Dtos.ColumnMapping("Code", "Name", null, "Rate", "Qty"));
        List<String[]> raw = List.of(
                new String[]{"Code", "Name", "Rate", "Qty"},
                new String[]{"111", "Coke 180MLx48Btls", "4363.64", "4"});
        CsvRowParser.Outcome outcome = CsvRowParser.applyCartonPricing(CsvRowParser.parseRows(raw, cols));

        assertEquals(1, outcome.rows().size());
        assertEquals(0, outcome.errors().size());
        assertBigEquals("90.91", outcome.rows().get(0).price());
        assertBigEquals("192", outcome.rows().get(0).qty());
    }

    @Test
    void applyCartonPricingFlagsRowsWithoutDetectableCount() {
        Map<String, Integer> cols = CsvRowParser.resolve(new String[]{"Code", "Name", "Rate", "Qty"},
                new com.shopinventory.web.dto.Dtos.ColumnMapping("Code", "Name", null, "Rate", "Qty"));
        List<String[]> raw = List.of(
                new String[]{"Code", "Name", "Rate", "Qty"},
                new String[]{"111", "Single Bottle 750ML", "100", "1"});
        CsvRowParser.Outcome outcome = CsvRowParser.applyCartonPricing(CsvRowParser.parseRows(raw, cols));

        assertEquals(0, outcome.rows().size());
        assertEquals(1, outcome.errors().size());
        assertTrue(outcome.errors().get(0).message().contains("carton"));
    }

    private static void assertBigEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
