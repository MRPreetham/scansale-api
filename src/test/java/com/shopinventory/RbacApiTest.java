package com.shopinventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopinventory.domain.user.OrgRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RbacApiTest extends BaseApiTest {

    @Test
    void salesStaffCannotCreateProduct() throws Exception {
        String token = createUserAndLogin("cashier@shop.local", OrgRole.SALES);
        JsonNode error = postJson("/api/v1/products", token,
                "{\"name\":\"X\",\"barcode\":\"999900001\"}", 403);
        assertEquals("forbidden", error.get("error").asText());
    }

    @Test
    void salesStaffCannotDeleteProduct() throws Exception {
        JsonNode product = createProduct(adminToken(), "Keep", "880800001", 10, 5);
        String token = createUserAndLogin("cashier2@shop.local", OrgRole.SALES);
        mockMvc.perform(delete("/api/v1/products/" + product.get("id").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void inventoryManagerCannotCreateSale() throws Exception {
        createProduct(adminToken(), "Item", "880800002", 15, 8);
        String token = createUserAndLogin("inv@shop.local", OrgRole.INVENTORY);
        JsonNode error = postJson("/api/v1/sales", token,
                "{\"lines\":[{\"barcode\":\"880800002\",\"qty\":1}],\"paymentMode\":\"CASH\"}", 403);
        assertEquals("forbidden", error.get("error").asText());
    }

    @Test
    void inventoryManagerCanCreateProductButCannotDelete() throws Exception {
        String token = createUserAndLogin("inv2@shop.local", OrgRole.INVENTORY);
        JsonNode product = postJson("/api/v1/products", token,
                "{\"name\":\"InvItem\",\"barcode\":\"880800003\",\"sellingPrice\":9,\"quantity\":2}", 201);
        assertTrue(product.has("id"));
        mockMvc.perform(delete("/api/v1/products/" + product.get("id").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void salesStaffCanCreateSaleAndViewProducts() throws Exception {
        String admin = adminToken();
        createProduct(admin, "Sellable", "880800004", 25, 10);
        String sales = createUserAndLogin("cashier3@shop.local", OrgRole.SALES);
        JsonNode sale = postJson("/api/v1/sales", sales,
                "{\"lines\":[{\"barcode\":\"880800004\",\"qty\":2}],\"paymentMode\":\"UPI\"}", 201);
        assertEquals("880800004", sale.get("lines").get(0).get("barcode").asText());
        JsonNode products = objectMapper.readTree(mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", bearer(sales)))
                .andReturn().getResponse().getContentAsString());
        assertTrue(products.get("items").size() >= 1);
    }

    @Test
    void salesStaffCannotAccessOrgSettings() throws Exception {
        String token = createUserAndLogin("cashier4@shop.local", OrgRole.SALES);
        mockMvc.perform(get("/api/v1/organization/settings").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }
}
