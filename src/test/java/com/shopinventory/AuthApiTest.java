package com.shopinventory;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends BaseApiTest {

    @Test
    void loginReturnsTokenWithRoleAndOrg() throws Exception {
        MvcResult result = loginRaw("admin@shop.local", "admin123", 200);
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotNull(response.get("token"));
        assertEquals("ADMIN", response.get("role").asText());
        assertEquals("admin@shop.local", response.get("email").asText());
        assertNotNull(response.get("orgId"));
        assertNotNull(response.get("orgName"));
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        loginRaw("admin@shop.local", "wrong-password", 401);
    }

    @Test
    void loginWithUnknownEmailFails() throws Exception {
        loginRaw("nobody@shop.local", "whatever", 401);
    }

    @Test
    void meReturnsAuthenticatedUserDetails() throws Exception {
        String token = adminToken();
        JsonNode me = objectMapper.readTree(mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
        assertEquals("admin@shop.local", me.get("email").asText());
        assertEquals("ADMIN", me.get("role").asText());
        assertEquals("Test Shop", me.get("orgName").asText());
    }

    @Test
    void meWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
