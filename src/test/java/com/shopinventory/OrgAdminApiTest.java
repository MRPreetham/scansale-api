package com.shopinventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopinventory.domain.user.OrgRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

class OrgAdminApiTest extends BaseApiTest {

    @Test
    void adminCanReadAndUpdateSettings() throws Exception {
        String token = adminToken();
        JsonNode settings = objectMapper.readTree(mockMvc.perform(get("/api/v1/organization/settings")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        assertEquals("Test Shop", settings.get("orgName").asText());

JsonNode updated = putJson("/api/v1/organization/settings", token,
                "{\"currency\":\"USD\",\"address\":\"Shop Street 1\",\"phone\":\"9876543210\",\"email\":\"shop@example.com\",\"gstin\":\"GST123\"}", 200);
        assertEquals("USD", updated.get("currency").asText());
        assertEquals("Shop Street 1", updated.get("address").asText());
        assertEquals("9876543210", updated.get("phone").asText());
        assertEquals("shop@example.com", updated.get("email").asText());
        assertEquals("GST123", updated.get("gstin").asText());
    }

    @Test
    void addUserCreatesMembership() throws Exception {
        String token = adminToken();
        JsonNode user = postJson("/api/v1/organization/users", token,
                "{\"email\":\"new@shop.local\",\"name\":\"New Guy\",\"password\":\"pw12345\",\"role\":\"SALES\"}", 201);
        assertEquals("new@shop.local", user.get("email").asText());
        assertEquals("SALES", user.get("role").asText());
    }

    @Test
    void ownerRoleIsNoLongerAccepted() throws Exception {
        String admin = createUserAndLogin("manager@shop.local", OrgRole.ADMIN);
        JsonNode error = postJson("/api/v1/organization/users", admin,
                "{\"email\":\"wannabe@shop.local\",\"name\":\"W\",\"password\":\"pw12345\",\"role\":\"OWNER\"}", 400);
        assertEquals("bad_request", error.get("error").asText());
    }

    @Test
    void adminCanAssignSalesRole() throws Exception {
        String admin = createUserAndLogin("manager2@shop.local", OrgRole.ADMIN);
        JsonNode user = postJson("/api/v1/organization/users", admin,
                "{\"email\":\"staff@shop.local\",\"name\":\"S\",\"password\":\"pw12345\",\"role\":\"INVENTORY\"}", 201);
        assertEquals("INVENTORY", user.get("role").asText());
    }

@Test
    void changingOwnRoleIsRejected() throws Exception {
        String token = adminToken();
        JsonNode error = patchJson("/api/v1/organization/users/" + adminUser.getId() + "/role", token,
                "{\"role\":\"ADMIN\"}", 400);
        assertEquals("bad_request", error.get("error").asText());
    }

    @Test
    void adminCanChangeAnotherUsersRole() throws Exception {
        String token = adminToken();
        JsonNode user = postJson("/api/v1/organization/users", token,
                "{\"email\":\"changer@shop.local\",\"name\":\"C\",\"password\":\"pw12345\",\"role\":\"SALES\"}", 201);
        JsonNode updated = objectMapper.readTree(mockMvc.perform(
                patch("/api/v1/organization/users/" + user.get("userId").asText() + "/role")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"INVENTORY\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals("INVENTORY", updated.get("role").asText());
    }

    @Test
    void removingAUserWorks() throws Exception {
        String token = adminToken();
        JsonNode user = postJson("/api/v1/organization/users", token,
                "{\"email\":\"gone@shop.local\",\"name\":\"G\",\"password\":\"pw12345\",\"role\":\"SALES\"}", 201);
        mockMvc.perform(delete("/api/v1/organization/users/" + user.get("userId").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void usersListShowsAllMembers() throws Exception {
        String token = adminToken();
        JsonNode users = objectMapper.readTree(mockMvc.perform(get("/api/v1/organization/users")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        assertTrue(users.size() >= 1);
    }
}
