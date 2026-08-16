package com.lastmilebanking.backend.config;

import com.lastmilebanking.backend.entity.User;
import com.lastmilebanking.backend.entity.UserRole;
import com.lastmilebanking.backend.entity.UserStatus;
import com.lastmilebanking.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile({"dev", "local"})
public class DevTestUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevTestUserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${LASTMILE_TEST_PASSWORD}")
    private String testPassword;

    public DevTestUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        if (testPassword == null || testPassword.trim().isEmpty() || testPassword.equals("${LASTMILE_TEST_PASSWORD}")) {
            log.error("CRITICAL ERROR: LASTMILE_TEST_PASSWORD environment variable is missing or empty.");
            throw new IllegalStateException("LASTMILE_TEST_PASSWORD is required for dev test user seeding.");
        }

        String username = "android-test-user";

        if (userRepository.existsByUsername(username)) {
            log.info("Test user '{}' already exists. Skipping creation.", username);
            return;
        }

        log.info("Creating test user '{}' for dev/local profile.", username);

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(testPassword));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        // Generate normally formatted userId
        String userId = "U" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        user.setUserId(userId);

        userRepository.save(user);

        log.info("Test user '{}' successfully created.", username);
    }
}
