package com.shopinventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopinventory.domain.user.OrgRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformApiTest extends BaseApiTest {

    private static final String ONBOARD_BODY =
            "{\"orgName\":\"Second Shop\",\"currency\":\"USD\",\"adminEmail\":\"admin2@shop.local\","
                    + "\"adminName\":\"New Admin\",\"adminPassword\":\"admin123\"}";

    private static final String TEAM_BODY =
            "{\"email\":\"support@shop.local\",\"name\":\"Support One\",\"password\":\"support123\","
                    + "\"platformRole\":\"SUPPORT\"}";

    private String superToken() throws Exception {
        return login("super@shop.local", "super123");
    }

    @Test
    void superAdminLoginHasNoOrgContext() throws Exception {
        MvcResult result = loginRaw("super@shop.local", "super123", 200);
        JsonNode login = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("SUPER_ADMIN", login.get("platformRole").asText());
        assertTrue(login.get("orgId").isNull());
        assertTrue(login.get("role").isNull());
    }

    @Test
    void platformUsersHaveNoTenantAccess() throws Exception {
        String superToken = superToken();
        JsonNode products = objectMapper.readTree(mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", bearer(superToken)))
                .andReturn().getResponse().getContentAsString());
        assertTrue(products.get("items").isEmpty());

        JsonNode error = postJson("/api/v1/products", superToken,
                "{\"name\":\"X\",\"barcode\":\"999900009\"}", 403);
        assertEquals("forbidden", error.get("error").asText());
    }

    @Test
    void superAdminCanOnboardOrgAndAdmin() throws Exception {
        JsonNode result = postJson("/api/v1/platform/organizations", superToken(), ONBOARD_BODY, 201);
        assertEquals("Second Shop", result.get("orgName").asText());
        assertEquals("admin2@shop.local", result.get("adminEmail").asText());
    }

    @Test
    void onboardedAdminCanLoginAsAdmin() throws Exception {
        postJson("/api/v1/platform/organizations", superToken(), ONBOARD_BODY, 201);
        MvcResult loginResult = loginRaw("admin2@shop.local", "admin123", 200);
        JsonNode login = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        assertEquals("Second Shop", login.get("orgName").asText());
        assertEquals(OrgRole.ADMIN.name(), login.get("role").asText());
    }

    @Test
    void regularOrgAdminCannotOnboard() throws Exception {
        JsonNode error = postJson("/api/v1/platform/organizations", adminToken(), ONBOARD_BODY, 403);
        assertEquals("forbidden", error.get("error").asText());
    }

    @Test
    void duplicateOrgNameRejected() throws Exception {
        String superToken = superToken();
        postJson("/api/v1/platform/organizations", superToken, ONBOARD_BODY, 201);
        JsonNode error = postJson("/api/v1/platform/organizations", superToken, ONBOARD_BODY, 409);
        assertEquals("conflict", error.get("error").asText());
    }

    @Test
    void superAdminCanSuspendAndResumeOrg() throws Exception {
        String superToken = superToken();
        JsonNode onboard = postJson("/api/v1/platform/organizations", superToken, ONBOARD_BODY, 201);
        String orgId = onboard.get("orgId").asText();

        patchJson("/api/v1/platform/organizations/" + orgId + "/status", superToken, "{\"status\":\"SUSPENDED\"}", 200);
        String suspendedToken = login("admin2@shop.local", "admin123");
        sendJson("GET", "/api/v1/products", suspendedToken, null, 401);

        patchJson("/api/v1/platform/organizations/" + orgId + "/status", superToken, "{\"status\":\"ACTIVE\"}", 200);
        sendJson("GET", "/api/v1/products", suspendedToken, null, 200);
    }

    @Test
    void superAdminCanUpdateOrgAdminDetails() throws Exception {
        String superToken = superToken();
        JsonNode onboard = postJson("/api/v1/platform/organizations", superToken, ONBOARD_BODY, 201);
        String orgId = onboard.get("orgId").asText();

        JsonNode updated = patchJson("/api/v1/platform/organizations/" + orgId + "/admin", superToken,
                "{\"email\":\"renamed@shop.local\",\"password\":\"newpass1\"}", 200);
        assertEquals("renamed@shop.local", updated.get("email").asText());
        loginRaw("admin2@shop.local", "admin123", 401);
        loginRaw("renamed@shop.local", "newpass1", 200);
    }

    @Test
    void updateAdminRejectsTakenEmail() throws Exception {
        String superToken = superToken();
        JsonNode onboard = postJson("/api/v1/platform/organizations", superToken, ONBOARD_BODY, 201);
        String orgId = onboard.get("orgId").asText();
        JsonNode error = patchJson("/api/v1/platform/organizations/" + orgId + "/admin", superToken,
                "{\"email\":\"admin@shop.local\"}", 409);
        assertEquals("conflict", error.get("error").asText());
    }

    @Test
    void superAdminCanCreateAndManagePlatformTeam() throws Exception {
        String superToken = superToken();
        JsonNode created = postJson("/api/v1/platform/team", superToken, TEAM_BODY, 201);
        assertEquals("SUPPORT", created.get("platformRole").asText());

        JsonNode team = objectMapper.readTree(mockMvc.perform(get("/api/v1/platform/team")
                        .header("Authorization", bearer(superToken)))
                .andReturn().getResponse().getContentAsString());
        assertTrue(team.size() >= 2);

        MvcResult loginResult = loginRaw("support@shop.local", "support123", 200);
        JsonNode login = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        assertEquals("SUPPORT", login.get("platformRole").asText());
    }

    @Test
    void supportCanViewStatsAndResetAdminButNotManageOrgs() throws Exception {
        String superToken = superToken();
        postJson("/api/v1/platform/team", superToken, TEAM_BODY, 201);
        String support = login("support@shop.local", "support123");

        sendJson("GET", "/api/v1/platform/organizations", support, null, 200);

        JsonNode onboard = postJson("/api/v1/platform/organizations", superToken, ONBOARD_BODY, 201);
        String orgId = onboard.get("orgId").asText();
        patchJson("/api/v1/platform/organizations/" + orgId + "/admin", support, "{\"email\":\"sres@shop.local\"}", 200);

        patchJson("/api/v1/platform/organizations/" + orgId + "/status", support, "{\"status\":\"SUSPENDED\"}", 403);
        postJson("/api/v1/platform/organizations", support, ONBOARD_BODY, 403);
    }

    @Test
    void supportCannotManagePlatformTeam() throws Exception {
        String superToken = superToken();
        postJson("/api/v1/platform/team", superToken, TEAM_BODY, 201);
        String support = login("support@shop.local", "support123");
        JsonNode error = postJson("/api/v1/platform/team", support, TEAM_BODY, 403);
        assertEquals("forbidden", error.get("error").asText());
    }
}
