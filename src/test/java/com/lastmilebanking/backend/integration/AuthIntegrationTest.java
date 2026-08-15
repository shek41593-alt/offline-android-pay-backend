package com.lastmilebanking.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastmilebanking.backend.dto.request.LoginRequest;
import com.lastmilebanking.backend.dto.request.RegisterRequest;
import com.lastmilebanking.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AuthIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        objectMapper = new ObjectMapper();
        userRepository.deleteAll();
    }

    @AfterEach
    public void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    public void testHealthPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    @Test
    public void testRegisterAndLogin() throws Exception {
        RegisterRequest register = new RegisterRequest();
        register.setUsername("user001");
        register.setPassword("securePassword");

        // 1. Register Valid User
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.username").value("user001"))
                .andExpect(jsonPath("$.role").value("USER"));

        // Verify password is hashed
        var user = userRepository.findByUsername("user001").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("securePassword");
        assertThat(user.getPasswordHash()).startsWith("$2a$"); // BCrypt

        // 2. Duplicate Registration
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        // 3. Login Valid Credentials
        LoginRequest login = new LoginRequest();
        login.setUsername("user001");
        login.setPassword("securePassword");

        String response = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("accessToken").asText();

        // 9. Valid token accessing protected endpoint (assuming transaction missing will return 400 or 404 but not 401)
        mockMvc.perform(get("/api/v1/transactions/TX_MISSING")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()); // Transaction not found, meaning 401 didn't happen!

        // 4. Login Wrong Password
        login.setPassword("wrongPassword");
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    public void testDisabledUserLogin() throws Exception {
        RegisterRequest register = new RegisterRequest();
        register.setUsername("user002");
        register.setPassword("securePassword");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        var user = userRepository.findByUsername("user002").orElseThrow();
        user.setStatus(com.lastmilebanking.backend.entity.UserStatus.DISABLED);
        userRepository.save(user);

        LoginRequest login = new LoginRequest();
        login.setUsername("user002");
        login.setPassword("securePassword");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized()); // Disabled user
    }

    @Test
    public void testMissingAndMalformedToken() throws Exception {
        // 6. Missing token
        mockMvc.perform(get("/api/v1/transactions/TX001"))
                .andExpect(status().isUnauthorized());

        // 7. Malformed token
        mockMvc.perform(get("/api/v1/transactions/TX001")
                .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

}
