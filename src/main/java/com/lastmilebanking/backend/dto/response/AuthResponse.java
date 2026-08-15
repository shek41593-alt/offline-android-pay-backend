package com.lastmilebanking.backend.dto.response;

public class AuthResponse {

    private String accessToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private String userId;
    private String username;
    private String role;

    public AuthResponse() {
    }

    public AuthResponse(String accessToken, long expiresIn, String userId, String username, String role) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
