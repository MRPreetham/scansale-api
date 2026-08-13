package com.shopinventory;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportApiTest extends BaseApiTest {

    @Test
    void dailyReportReflectsSalesAndAdjustments() throws Exception {
        String token = adminToken();
        JsonNode product = createProduct(token, "Cola", "4440000001", 40, 100);
        postJson("/api/v1/sales", token,
                "{\"lines\":[{\"barcode\":\"4440000001\",\"qty\":3}],\"paymentMode\":\"CASH\"}", 201);
        postJson("/api/v1/products/" + product.get("id").asText() + "/stock/adjust", token,
                "{\"newQuantity\":90,\"reason\":\"count\"}", 200);

        String today = LocalDate.now().toString();
        JsonNode report = objectMapper.readTree(mockMvc.perform(get("/api/v1/reports/daily?date=" + today)
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());

assertEquals(today, report.get("date").asText());
        assertBigEquals("120.00", report.get("totalSalesAmount"));
        assertBigEquals("3.000", report.get("totalUnitsSold"));
        assertBigEquals("120.00", report.get("paymentBreakdown").get("CASH"));

        JsonNode row = report.get("rows").get(0);
        assertBigEquals("0.000", row.get("openingQty"));
        assertBigEquals("100.000", row.get("placedQty"));
        assertBigEquals("3.000", row.get("soldQty"));
        assertBigEquals("90.000", row.get("endQty"));
    }

    @Test
    void invalidDateIsBadRequest() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/v1/reports/daily?date=not-a-date")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }
}
