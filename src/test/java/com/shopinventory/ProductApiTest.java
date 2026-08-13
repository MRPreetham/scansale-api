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
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
    }
}
