package com.shopinventory;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class ImportApiTest extends BaseApiTest {

    private JsonNode preview(String token, String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "stock.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/v1/imports/preview")
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void previewReportsNewAndUpdateCountsAndRowErrors() throws Exception {
        String token = adminToken();
        createProduct(token, "Existing", "3330000002", 5, 1);

        String csv = "name,barcode,unit,price,qty\n"
                + "NewOne,3330000001,pcs,40,100\n"
                + "Existing,3330000002,pcs,6,50\n"
                + "BadRow,,pcs,5,1";
        JsonNode preview = preview(token, csv);

        assertEquals(1, preview.get("newCount").asInt());
        assertEquals(1, preview.get("updateCount").asInt());
        assertTrue(preview.get("skipCount").asInt() >= 1);
        assertTrue(preview.get("errors").size() >= 1);
    }

    @Test
    void commitCreatesNewProductsAndAppliesOpeningStock() throws Exception {
        String token = adminToken();
        String csv = "name,barcode,unit,price,qty\n"
                + "Tea,3330000003,pcs,80,25";
        JsonNode preview = preview(token, csv);
        String importId = preview.get("importId").asText();

        JsonNode commit = objectMapper.readTree(mockMvc.perform(post("/api/v1/imports/" + importId + "/commit")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, commit.get("newCount").asInt());

        JsonNode tea = objectMapper.readTree(mockMvc.perform(get("/api/v1/products?q=tea")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, tea.get("items").size());
        assertBigEquals("25.000", tea.get("items").get(0).get("availableQty"));
    }

    @Test
    void commitIncrementsExistingStock() throws Exception {
        String token = adminToken();
        createProduct(token, "UpdateMe", "3330000004", 5, 10);
        String csv = "name,barcode,unit,price,qty\n"
                + "UpdateMe,3330000004,pcs,5,80";
        JsonNode preview = preview(token, csv);
        mockMvc.perform(post("/api/v1/imports/" + preview.get("importId").asText() + "/commit")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated());

        JsonNode updated = objectMapper.readTree(mockMvc.perform(get("/api/v1/products?q=updateme")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        // 10 existing + 80 imported = 90
        assertBigEquals("90.000", updated.get("items").get(0).get("availableQty"));
    }

    @Test
    void commitCreatesProductForUnknownBarcode() throws Exception {
        String token = adminToken();
        String csv = "name,barcode,unit,price,qty\n"
                + "Mystery,3330000006,pcs,12,7";
        JsonNode preview = preview(token, csv);
        assertEquals(1, preview.get("newCount").asInt());

        mockMvc.perform(post("/api/v1/imports/" + preview.get("importId").asText() + "/commit")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated());

        JsonNode created = objectMapper.readTree(mockMvc.perform(get("/api/v1/products?q=mystery")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        assertBigEquals("7.000", created.get("items").get(0).get("availableQty"));
    }

    @Test
    void committingTwiceIsRejected() throws Exception {
        String token = adminToken();
        String csv = "name,barcode,unit,price,qty\n"
                + "Once,3330000005,pcs,1,1";
        JsonNode preview = preview(token, csv);
        String importId = preview.get("importId").asText();
        mockMvc.perform(post("/api/v1/imports/" + importId + "/commit")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated());
        JsonNode error = objectMapper.readTree(mockMvc.perform(post("/api/v1/imports/" + importId + "/commit")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        assertEquals("conflict", error.path("error").asText());
    }

    @Test
    void historyListsImportsWithRealCounts() throws Exception {
        String token = adminToken();
        String csv = "name,barcode,unit,price,qty\n"
                + "Historic,3330000007,pcs,4,9";
        JsonNode preview = preview(token, csv);
        mockMvc.perform(post("/api/v1/imports/" + preview.get("importId").asText() + "/commit")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated());

        JsonNode history = objectMapper.readTree(mockMvc.perform(get("/api/v1/imports")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, history.get("items").size());
        assertEquals(1, history.get("items").get(0).get("newCount").asInt());
        assertEquals(0, history.get("items").get(0).get("updateCount").asInt());
    }
}
