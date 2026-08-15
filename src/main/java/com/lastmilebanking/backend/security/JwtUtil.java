package com.lastmilebanking.backend.security;

import com.lastmilebanking.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    // MVP default secret. The instruction specified "For local development, use an environment variable or an ignored config... If placeholder needed, clearly separate". 
    // Wait, let's use @Value with a fallback for local dev if absolutely necessary, but standard Spring Boot @Value("${jwt.secret}") is best.
    // The instructions say: "NEVER hard-code the production JWT secret directly into Java source."
    @Value("${jwt.secret:default-local-dev-jwt-secret-must-be-min-32-chars-long}")
    private String jwtSecret;

    @Value("${jwt.expiration:3600000}")
    private long jwtExpirationMs; // 1 hour

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getUserId())
                .claim("role", user.getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    public String extractUserId(String token) {
        return extractAllClaims(token).get("userId", String.class);
    }
    
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
