package com.shopinventory;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class SaleApiTest extends BaseApiTest {

    @Test
    void saleDecrementsStockAndNumbering() throws Exception {
        String token = adminToken();
        JsonNode product = createProduct(token, "Cola", "2220000001", 40, 100);
        JsonNode sale = postJson("/api/v1/sales", token,
                "{\"lines\":[{\"barcode\":\"2220000001\",\"qty\":3}],\"paymentMode\":\"CASH\"}", 201);
        assertEquals("SLS-0001/2026", sale.get("saleNumber").asText());
        assertBigEquals("120.00", sale.get("totalAmount"));
        assertBigEquals("3.000", sale.get("totalQty"));

        JsonNode after = objectMapper.readTree(mockMvc.perform(get("/api/v1/products/" + product.get("id").asText())
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        assertBigEquals("97.000", after.get("availableQty"));
    }

    @Test
    void duplicateBarcodesInOneSaleAreMerged() throws Exception {
        String token = adminToken();
        createProduct(token, "Mint", "2220000002", 10, 10);
        JsonNode sale = postJson("/api/v1/sales", token,
                "{\"lines\":[{\"barcode\":\"2220000002\",\"qty\":2},{\"barcode\":\"2220000002\",\"qty\":3}],\"paymentMode\":\"CASH\"}", 201);
        assertEquals(1, sale.get("lines").size());
        assertBigEquals("5.000", sale.get("lines").get(0).get("qty"));
    }

    @Test
    void oversellIsBlockedWithConflict() throws Exception {
        String token = adminToken();
        createProduct(token, "Scarce", "2220000003", 100, 2);
        JsonNode error = postJson("/api/v1/sales", token,
                "{\"lines\":[{\"barcode\":\"2220000003\",\"qty\":5}],\"paymentMode\":\"CASH\"}", 409);
        assertEquals("conflict", error.get("error").asText());
        assertTrue(error.get("message").asText().contains("available"));
    }

    @Test
    void saleKeepsPriorStockIntactWhenOversellAttemptFails() throws Exception {
        String token = adminToken();
        JsonNode product = createProduct(token, "Guard", "2220000004", 10, 4);
        postJson("/api/v1/sales", token,
                "{\"lines\":[{\"barcode\":\"2220000004\",\"qty\":999}]}", 409);
        JsonNode after = objectMapper.readTree(mockMvc.perform(get("/api/v1/products/" + product.get("id").asText())
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        assertBigEquals("4.000", after.get("availableQty"));
    }

    @Test
    void saleWithUnknownBarcodeFails() throws Exception {
        String token = adminToken();
        JsonNode error = postJson("/api/v1/sales", token,
                "{\"lines\":[{\"barcode\":\"9999999999\",\"qty\":1}]}", 404);
        assertEquals("not_found", error.get("error").asText());
    }

    @Test
    void saleRequiresAtLeastOneLine() throws Exception {
        String token = adminToken();
        JsonNode error = postJson("/api/v1/sales", token,
                "{\"lines\":[],\"paymentMode\":\"CASH\"}", 400);
        assertEquals("bad_request", error.get("error").asText());
    }

    @Test
    void zeroQuantityIsRejected() throws Exception {
        String token = adminToken();
        createProduct(token, "Zero", "2220000005", 10, 5);
        JsonNode error = postJson("/api/v1/sales", token,
                "{\"lines\":[{\"barcode\":\"2220000005\",\"qty\":0}]}", 400);
        assertEquals("bad_request", error.get("error").asText());
    }

    @Test
    void listSalesReturnsCreatedSales() throws Exception {
        String token = adminToken();
        createProduct(token, "List", "2220000006", 10, 5);
        postJson("/api/v1/sales", token,
                "{\"lines\":[{\"barcode\":\"2220000006\",\"qty\":1}],\"paymentMode\":\"UPI\"}", 201);
        JsonNode sales = objectMapper.readTree(mockMvc.perform(get("/api/v1/sales")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, sales.get("items").size());
    }

    @Test
    void saleResponseIncludesShopDetailsAndLineSnapshot() throws Exception {
        String token = adminToken();
        postJson("/api/v1/products", token,
                "{\"name\":\"Cola Bottle\",\"barcode\":\"2220000007\",\"unit\":\"ml\","
                        + "\"costPrice\":80,\"sellingPrice\":100,\"profitMargin\":25,\"size\":750,\"openingQty\":10}", 201);
        JsonNode sale = postJson("/api/v1/sales", token,
                "{\"lines\":[{\"barcode\":\"2220000007\",\"qty\":2}],\"paymentMode\":\"CASH\"}", 201);
        assertEquals("Test Shop", sale.get("shop").get("name").asText());
        assertEquals("INR", sale.get("shop").get("currency").asText());
        assertEquals("ml", sale.get("lines").get(0).get("unit").asText());
        assertBigEquals("750.000", sale.get("lines").get(0).get("size"));
    }
}