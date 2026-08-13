package com.shopinventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopinventory.domain.organization.Organization;
import com.shopinventory.domain.organization.OrganizationRepository;
import com.shopinventory.domain.user.Membership;
import com.shopinventory.domain.user.MembershipRepository;
import com.shopinventory.domain.user.MembershipStatus;
import com.shopinventory.domain.user.OrgRole;
import com.shopinventory.domain.user.User;
import com.shopinventory.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseApiTest {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected MembershipRepository membershipRepository;
    @Autowired
    protected OrganizationRepository organizationRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected Organization org;
    protected User adminUser;

    @BeforeEach
    void setUpBase() {
        adminUser = userRepository.findByEmailIgnoreCase("admin@shop.local").orElseThrow();
        org = membershipRepository.findByUserId(adminUser.getId()).stream()
                .findFirst()
                .map(Membership::getOrg)
                .orElseGet(() -> organizationRepository.findAll().get(0));
    }

    protected MvcResult loginRaw(String email, String password, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    protected String login(String email, String password) throws Exception {
        MvcResult result = loginRaw(email, password, 200);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    protected String adminToken() throws Exception {
        return login("admin@shop.local", "admin123");
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected JsonNode postJson(String path, String token, String body, int expectedStatus) throws Exception {
        return sendJson("POST", path, token, body, expectedStatus);
    }

    protected JsonNode putJson(String path, String token, String body, int expectedStatus) throws Exception {
        return sendJson("PUT", path, token, body, expectedStatus);
    }

    protected JsonNode patchJson(String path, String token, String body, int expectedStatus) throws Exception {
        return sendJson("PATCH", path, token, body, expectedStatus);
    }

    protected JsonNode sendJson(String method, String path, String token, String body, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder builder =
                request(
                        HttpMethod.valueOf(method), path);
        if (token != null) {
            builder.header("Authorization", bearer(token));
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return jsonIfAny(result);
    }

    protected void deleteJson(String path, String token, int expectedStatus) throws Exception {
        mockMvc.perform(delete(path)
                        .header("Authorization", bearer(token)))
                .andExpect(status().is(expectedStatus));
    }

    protected JsonNode jsonIfAny(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        return content == null || content.isBlank() ? null : objectMapper.readTree(content);
    }

    protected String createUserAndLogin(String email, OrgRole role) throws Exception {
        User user = new User();
        user.setEmail(email);
        user.setName(role.name().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode("pass1234"));
        user = userRepository.save(user);

        Membership membership = new Membership();
        membership.setOrg(org);
        membership.setUser(user);
        membership.setRole(role);
        membership.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(membership);
        return login(email, "pass1234");
    }

    protected JsonNode createProduct(String token, String name, String barcode, double price, double qty) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"barcode\":\"" + barcode + "\",\"unit\":\"pcs\","
                + "\"sellingPrice\":" + price + ",\"openingQty\":" + qty + ",\"reorderLevel\":5}";
        return postJson("/api/v1/products", token, body, 201);
    }

    protected void assertBigEquals(String expected, JsonNode node) {
        java.math.BigDecimal expectedValue = new java.math.BigDecimal(expected);
        java.math.BigDecimal actualValue = new java.math.BigDecimal(node.asText());
        assertEquals(0, expectedValue.compareTo(actualValue),
                "expected " + expected + " but was " + node.asText());
    }
}