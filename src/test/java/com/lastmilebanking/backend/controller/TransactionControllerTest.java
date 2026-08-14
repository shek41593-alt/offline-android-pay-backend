package com.lastmilebanking.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lastmilebanking.backend.dto.request.SyncTransactionRequest;
import com.lastmilebanking.backend.dto.response.SyncTransactionResponse;
import com.lastmilebanking.backend.entity.TransactionStatus;
import com.lastmilebanking.backend.exception.GlobalExceptionHandler;
import com.lastmilebanking.backend.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;


@ExtendWith(MockitoExtension.class)
public class TransactionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private TransactionController transactionController;

    private ObjectMapper objectMapper;
    private SyncTransactionRequest validRequest;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(transactionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        validRequest = new SyncTransactionRequest();
        validRequest.setTransactionId("TX001");
        validRequest.setSenderId("SENDER1");
        validRequest.setReceiverId("RECEIVER1");
        validRequest.setAmount(new BigDecimal("500.00"));
        validRequest.setCurrency("INR");
        validRequest.setPaymentMode("QR");
        validRequest.setTimestamp(Instant.parse("2026-08-14T10:00:00Z"));
        validRequest.setSignature("TEST_SIG");
    }

    @Test
    void createTransaction_validRequest_returns201AndResponse() throws Exception {
        SyncTransactionResponse response = new SyncTransactionResponse(
                "TX001", TransactionStatus.RECEIVED, "Transaction received successfully"
        );

        when(transactionService.processTransaction(any(SyncTransactionRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value("TX001"))
                .andExpect(jsonPath("$.status").value(TransactionStatus.RECEIVED.name()))
                .andExpect(jsonPath("$.message").value("Transaction received successfully"));

        verify(transactionService, times(1)).processTransaction(any(SyncTransactionRequest.class));
    }

    @Test
    void createTransaction_missingTransactionId_returns400AndValidationError() throws Exception {
        validRequest.setTransactionId(null);

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("is required")))
                .andExpect(jsonPath("$.path").value("/api/v1/transactions"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(transactionService, never()).processTransaction(any(SyncTransactionRequest.class));
    }

    @Test
    void createTransaction_malformedJson_returns400AndBadRequestError() throws Exception {
        String malformedJson = "{ \"transactionId\": \"TX001\", \"amount\": \"not_a_number\" }";

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed or invalid request body"))
                .andExpect(jsonPath("$.path").value("/api/v1/transactions"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(transactionService, never()).processTransaction(any(SyncTransactionRequest.class));
    }

    @Test
    void createTransaction_duplicateTransaction_returns200AndDuplicateStatus() throws Exception {
        SyncTransactionResponse response = new SyncTransactionResponse(
                "TX001", TransactionStatus.DUPLICATE, "Transaction already exists"
        );

        when(transactionService.processTransaction(any(SyncTransactionRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TX001"))
                .andExpect(jsonPath("$.status").value(TransactionStatus.DUPLICATE.name()));

        verify(transactionService, times(1)).processTransaction(any(SyncTransactionRequest.class));
    }

    @Test
    void createTransaction_serviceFailure_returns500AndInternalServerError() throws Exception {
        when(transactionService.processTransaction(any(SyncTransactionRequest.class)))
                .thenThrow(new RuntimeException("Service failed unexpectedly"));

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected server error occurred"))
                .andExpect(jsonPath("$.path").value("/api/v1/transactions"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void createTransaction_persistenceFailure_returns500AndInternalServerError() throws Exception {
        when(transactionService.processTransaction(any(SyncTransactionRequest.class)))
                .thenThrow(new DataIntegrityViolationException("Database fails"));

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An internal server error occurred"))
                .andExpect(jsonPath("$.path").value("/api/v1/transactions"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
