package com.lastmilebanking.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lastmilebanking.backend.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper mapper;

    public CustomAuthenticationEntryPoint() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHORIZED",
                "Authentication is required",
                request.getRequestURI()
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
