package com.shopinventory;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class ProductApiTest extends BaseApiTest {

@Test
    void createProductSetsOpeningQuantity() throws Exception {
        String token = adminToken();
        JsonNode product = createProduct(token, "Cola", "1110000001", 40, 100);
        assertEquals("1110000001", product.get("barcode").asText());
        assertBigEquals("100.000", product.get("availableQty"));
        assertFalse(product.get("lowStock").asBoolean());
    }

    @Test
    void duplicateBarcodeIsRejected() throws Exception {
        String token = adminToken();
        createProduct(token, "First", "1110000002", 10, 1);
        JsonNode error = postJson("/api/v1/products", token,
                "{\"name\":\"Second\",\"barcode\":\"1110000002\"}", 409);
        assertEquals("conflict", error.get("error").asText());
    }

    @Test
    void productListSupportsSearchAndLowStockFilter() throws Exception {
        String token = adminToken();
        createProduct(token, "Apple", "1110000003", 5, 3);
        createProduct(token, "Banana", "1110000004", 6, 2);

        JsonNode search = objectMapper.readTree(mockMvc.perform(get("/api/v1/products?q=apple")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, search.get("items").size());
        assertEquals("Apple", search.get("items").get(0).get("name").asText());

        JsonNode low = objectMapper.readTree(mockMvc.perform(get("/api/v1/products?low=true")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        boolean allLow = true;
        for (JsonNode p : low.get("items")) {
            allLow &= p.get("lowStock").asBoolean();
        }
        assertTrue(allLow);
    }

@Test
    void updateProductChangesFields() throws Exception {
        String token = adminToken();
        JsonNode product = createProduct(token, "Before", "1110000005", 10, 5);
        JsonNode updated = putJson("/api/v1/products/" + product.get("id").asText(), token,
                "{\"name\":\"After\",\"barcode\":\"1110000005\",\"sellingPrice\":12}", 200);
        assertEquals("After", updated.get("name").asText());
    }

@Test
    void updateProductRejectsBarcodeTakenByAnotherProduct() throws Exception {
        String token = adminToken();
        JsonNode a = createProduct(token, "A", "1110000006", 10, 1);
        createProduct(token, "B", "1110000007", 10, 1);
        JsonNode error = putJson("/api/v1/products/" + a.get("id").asText(), token,
                "{\"name\":\"A\",\"barcode\":\"1110000007\"}", 409);
        assertEquals("conflict", error.get("error").asText());
    }

@Test
    void deleteProductWithoutMovementsWorks() throws Exception {
        String token = adminToken();
        JsonNode product = createProduct(token, "Gone", "1110000008", 10, 0);
        deleteJson("/api/v1/products/" + product.get("id").asText(), token, 204);
        mockMvc.perform(get("/api/v1/products/" + product.get("id").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProductWithStockMovementsIsRejected() throws Exception {
        String token = adminToken();
        JsonNode product = createProduct(token, "SoldBefore", "1110000088", 10, 5);
        mockMvc.perform(delete("/api/v1/products/" + product.get("id").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
    }

@Test
    void adjustStockChangesAvailableAndLogsMovement() throws Exception {
        String token = adminToken();
        JsonNode product = createProduct(token, "Adjust", "1110000009", 20, 10);
        JsonNode adjusted = postJson("/api/v1/products/" + product.get("id").asText() + "/stock/adjust", token,
                "{\"newQuantity\":6,\"reason\":\"physical count\"}", 200);
        assertBigEquals("6.000", adjusted.get("availableQty"));
    }

    @Test
    void adjustStockRejectsNegativeQuantity() throws Exception {
        String token = adminToken();
        JsonNode product = createProduct(token, "AdjustNeg", "1110000010", 20, 10);
        JsonNode error = postJson("/api/v1/products/" + product.get("id").asText() + "/stock/adjust", token,
                "{\"newQuantity\":-1}", 400);
        assertEquals("bad_request", error.get("error").asText());
    }

    @Test
    void createAndUpdateRoundTripCostProfitSize() throws Exception {
        String token = adminToken();
        JsonNode created = postJson("/api/v1/products", token,
                "{\"name\":\"Cola Bottle\",\"barcode\":\"1110000020\",\"unit\":\"ml\","
                        + "\"costPrice\":80,\"sellingPrice\":100,\"profitMargin\":25,\"size\":750,"
                        + "\"openingQty\":10}", 201);
        assertBigEquals("80.00", created.get("costPrice"));
        assertBigEquals("100.00", created.get("sellingPrice"));
        assertBigEquals("25.00", created.get("profitMargin"));
        assertBigEquals("750.000", created.get("size"));

        JsonNode updated = putJson("/api/v1/products/" + created.get("id").asText(), token,
                "{\"name\":\"Cola Bottle\",\"barcode\":\"1110000020\","
                        + "\"costPrice\":90,\"sellingPrice\":120,\"profitMargin\":33.33,\"size\":1000}", 200);
        assertBigEquals("90.00", updated.get("costPrice"));
        assertBigEquals("120.00", updated.get("sellingPrice"));
        assertBigEquals("33.33", updated.get("profitMargin"));
        assertBigEquals("1000.000", updated.get("size"));
    }

    @Test
    void updateQuantityAdjustsAvailableStock() throws Exception {
        String token = adminToken();
        JsonNode product = createProduct(token, "Stock", "1110000021", 20, 5);
        assertBigEquals("5.000", product.get("availableQty"));
        JsonNode updated = putJson("/api/v1/products/" + product.get("id").asText(), token,
                "{\"name\":\"Stock\",\"barcode\":\"1110000021\",\"openingQty\":8}", 200);
        assertBigEquals("8.000", updated.get("availableQty"));
    }

    @Test
    void updateWithoutQuantityKeepsStock() throws Exception {
        String token = adminToken();
        JsonNode product = createProduct(token, "Keep", "1110000023", 20, 7);
        JsonNode updated = putJson("/api/v1/products/" + product.get("id").asText(), token,
                "{\"name\":\"Keep\",\"barcode\":\"1110000023\",\"sellingPrice\":22}", 200);
        assertBigEquals("7.000", updated.get("availableQty"));
    }

    @Test
    void updateRejectsNegativeQuantity() throws Exception {
        String token = adminToken();
        JsonNode product = createProduct(token, "Neg", "1110000022", 20, 5);
        JsonNode error = putJson("/api/v1/products/" + product.get("id").asText(), token,
                "{\"name\":\"Neg\",\"barcode\":\"1110000022\",\"openingQty\":-1}", 400);
        assertEquals("bad_request", error.get("error").asText());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
    }
}
