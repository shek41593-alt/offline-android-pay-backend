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

        String targetUsername = "9876543210";
        String legacyUsername = "android-test-user";

        java.util.Optional<User> legacyUserOpt = userRepository.findByUsername(legacyUsername);
        java.util.Optional<User> targetUserOpt = userRepository.findByUsername(targetUsername);

        if (legacyUserOpt.isPresent()) {
            if (targetUserOpt.isPresent()) {
                log.info("Both legacy '{}' and target '{}' exist. Deleting legacy and updating target.", legacyUsername, targetUsername);
                userRepository.delete(legacyUserOpt.get());
                User targetUser = targetUserOpt.get();
                targetUser.setPasswordHash(passwordEncoder.encode(testPassword));
                userRepository.save(targetUser);
            } else {
                log.info("Migrating legacy test user '{}' to '{}'.", legacyUsername, targetUsername);
                User legacyUser = legacyUserOpt.get();
                legacyUser.setUsername(targetUsername);
                legacyUser.setPasswordHash(passwordEncoder.encode(testPassword));
                userRepository.save(legacyUser);
            }
            return;
        }

        if (targetUserOpt.isPresent()) {
            log.info("Test user '{}' already exists. Updating encoded password.", targetUsername);
            User targetUser = targetUserOpt.get();
            targetUser.setPasswordHash(passwordEncoder.encode(testPassword));
            userRepository.save(targetUser);
            return;
        }

        log.info("Creating test user '{}' for dev/local profile.", targetUsername);

        User user = new User();
        user.setUsername(targetUsername);
        user.setPasswordHash(passwordEncoder.encode(testPassword));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        // Generate normally formatted userId
        String userId = "U" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        user.setUserId(userId);

        userRepository.save(user);

        log.info("Test user '{}' successfully created.", targetUsername);
    }
}
