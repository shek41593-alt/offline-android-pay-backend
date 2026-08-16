package com.lastmilebanking.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastmilebanking.backend.dto.request.LoginRequest;
import com.lastmilebanking.backend.entity.User;
import com.lastmilebanking.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"LASTMILE_TEST_PASSWORD=dev-secure-password"})
@ActiveProfiles("dev")
public class DevTestUserSeederIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private com.lastmilebanking.backend.config.DevTestUserSeeder devTestUserSeeder;

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
    }

    @AfterEach
    public void tearDown() {
        // Keep the database clean except what seeder did
        // Seeder runs on context startup, so we shouldn't delete it if we want to test login repeatedly
        // Actually, let's keep it clean by finding and deleting specific test users, or just let Spring's dirtiesContext handle it
    }

    @Test
    public void testUserSeededCorrectlyUnderDevProfile() {
        Optional<User> optionalUser = userRepository.findByUsername("android-test-user");
        assertThat(optionalUser).isPresent();

        User user = optionalUser.get();
        assertThat(user.getRole().name()).isEqualTo("USER");
        assertThat(user.getUserId()).startsWith("U");
        assertThat(user.getPasswordHash()).startsWith("$2a$"); // BCrypt encoding
        assertThat(user.getPasswordHash()).isNotEqualTo("dev-secure-password"); // Plaintext not stored
    }

    @Test
    public void testExistingUserPasswordIsUpdated() throws Exception {
        // Change the password in DB manually to something else
        User user = userRepository.findByUsername("android-test-user").get();
        user.setPasswordHash("manually-changed-hash");
        userRepository.save(user);

        // Run the seeder again (it should update the password)
        devTestUserSeeder.run();

        // Verify it was changed back
        User updatedUser = userRepository.findByUsername("android-test-user").get();
        assertThat(updatedUser.getPasswordHash()).isNotEqualTo("manually-changed-hash");
        assertThat(updatedUser.getPasswordHash()).startsWith("$2a$");

        // Verify login works again
        LoginRequest login = new LoginRequest();
        login.setUsername("android-test-user");
        login.setPassword("dev-secure-password");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk());
    }

    @Test
    public void testLoginWithDevCredentialsSucceeds() throws Exception {
        LoginRequest login = new LoginRequest();
        login.setUsername("android-test-user");
        login.setPassword("dev-secure-password");

        String response = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("android-test-user"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("accessToken").asText();

        // Verify protected endpoint access
        mockMvc.perform(get("/api/v1/transactions/TX_MISSING")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()); // NotFound implies auth passed
    }

    @Test
    public void testLoginWithWrongPasswordFails() throws Exception {
        LoginRequest login = new LoginRequest();
        login.setUsername("android-test-user");
        login.setPassword("wrong-password");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }
}
