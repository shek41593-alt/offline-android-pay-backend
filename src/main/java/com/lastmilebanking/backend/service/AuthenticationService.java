package com.lastmilebanking.backend.service;

import com.lastmilebanking.backend.dto.request.LoginRequest;
import com.lastmilebanking.backend.dto.request.RegisterRequest;
import com.lastmilebanking.backend.dto.response.AuthResponse;
import com.lastmilebanking.backend.dto.response.RegisterResponse;
import com.lastmilebanking.backend.entity.User;
import com.lastmilebanking.backend.entity.UserRole;
import com.lastmilebanking.backend.entity.UserStatus;
import com.lastmilebanking.backend.repository.UserRepository;
import com.lastmilebanking.backend.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                 AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            // Because GlobalExceptionHandler doesn't specifically have UserExists, I will throw IllegalArgumentException or a generic one if no specific is available.
            // But wait, the instruction says "Expected: HTTP 409 or the existing appropriate conflict response."
            // Existing ones: IdempotencyConflictException, CurrencyMismatchException? Let's just create UserAlreadyExistsException and handle it.
            throw new com.lastmilebanking.backend.exception.UserAlreadyExistsException("Username already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        // Generate userId (e.g. U-...)
        String userId = "U" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        user.setUserId(userId);

        User savedUser = userRepository.save(user);

        return new RegisterResponse(savedUser.getUserId(), savedUser.getUsername(), savedUser.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = (User) authentication.getPrincipal();

        String token = jwtUtil.generateToken(user);
        
        // expiresIn could be retrieved from jwtUtil, here we hardcode the configuration value 3600
        return new AuthResponse(token, 3600, user.getUserId(), user.getUsername(), user.getRole().name());
    }
}
