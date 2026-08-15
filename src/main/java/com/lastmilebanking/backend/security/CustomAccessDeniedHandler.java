package com.lastmilebanking.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lastmilebanking.backend.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper mapper;

    public CustomAccessDeniedHandler() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN",
                "You are not authorized to access this resource",
                request.getRequestURI()
        );

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
