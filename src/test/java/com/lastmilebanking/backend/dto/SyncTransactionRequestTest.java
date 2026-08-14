package com.lastmilebanking.backend.dto;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.lastmilebanking.backend.dto.request.SyncTransactionRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SyncTransactionRequestTest {

    private static Validator validator;
    private static ObjectMapper objectMapper;

    @BeforeAll
    public static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    private SyncTransactionRequest createValidRequest() {
        SyncTransactionRequest request = new SyncTransactionRequest();
        request.setTransactionId("TX001");
        request.setSenderId("USER001");
        request.setReceiverId("MERCHANT001");
        request.setAmount(new BigDecimal("500.00"));
        request.setCurrency("INR");
        request.setPaymentMode("QR");
        request.setTimestamp(Instant.parse("2026-08-14T10:00:00Z"));
        request.setSignature("TEST_SIGNATURE");
        return request;
    }

    @Test
    public void test1_validRequest() {
        SyncTransactionRequest request = createValidRequest();
        Set<ConstraintViolation<SyncTransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    public void test2_missingTransactionId() {
        SyncTransactionRequest request = createValidRequest();
        request.setTransactionId(null);
        Set<ConstraintViolation<SyncTransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    public void test3_blankTransactionId() {
        SyncTransactionRequest request = createValidRequest();
        request.setTransactionId("");
        Set<ConstraintViolation<SyncTransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    public void test4_missingSenderId() {
        SyncTransactionRequest request = createValidRequest();
        request.setSenderId(null);
        Set<ConstraintViolation<SyncTransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    public void test5_missingReceiverId() {
        SyncTransactionRequest request = createValidRequest();
        request.setReceiverId(null);
        Set<ConstraintViolation<SyncTransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    public void test6_missingAmount() {
        SyncTransactionRequest request = createValidRequest();
        request.setAmount(null);
        Set<ConstraintViolation<SyncTransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    public void test7_zeroAmount() {
        SyncTransactionRequest request = createValidRequest();
        request.setAmount(BigDecimal.ZERO);
        Set<ConstraintViolation<SyncTransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    public void test8_negativeAmount() {
        SyncTransactionRequest request = createValidRequest();
        request.setAmount(new BigDecimal("-100.00"));
        Set<ConstraintViolation<SyncTransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    public void test9_missingCurrency() {
        SyncTransactionRequest request = createValidRequest();
        request.setCurrency(null);
        Set<ConstraintViolation<SyncTransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    public void test10_missingPaymentMode() {
        SyncTransactionRequest request = createValidRequest();
        request.setPaymentMode(null);
        Set<ConstraintViolation<SyncTransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    public void test11_missingTimestamp() {
        SyncTransactionRequest request = createValidRequest();
        request.setTimestamp(null);
        Set<ConstraintViolation<SyncTransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    public void test12_validRequestSignatureOmitted() {
        SyncTransactionRequest request = createValidRequest();
        request.setSignature(null);
        Set<ConstraintViolation<SyncTransactionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    public void testJsonMapping() throws Exception {
        String json = "{\n" +
                "  \"transactionId\": \"TX001\",\n" +
                "  \"senderId\": \"USER001\",\n" +
                "  \"receiverId\": \"MERCHANT001\",\n" +
                "  \"amount\": 500.00,\n" +
                "  \"currency\": \"INR\",\n" +
                "  \"paymentMode\": \"QR\",\n" +
                "  \"timestamp\": \"2026-08-14T10:00:00Z\",\n" +
                "  \"signature\": \"TEST_SIGNATURE\"\n" +
                "}";

        SyncTransactionRequest request = objectMapper.readValue(json, SyncTransactionRequest.class);
        assertEquals("TX001", request.getTransactionId());
        assertEquals("USER001", request.getSenderId());
        assertEquals("MERCHANT001", request.getReceiverId());
        assertEquals(0, new BigDecimal("500.00").compareTo(request.getAmount()));
        assertEquals("INR", request.getCurrency());
        assertEquals("QR", request.getPaymentMode());
        assertEquals(Instant.parse("2026-08-14T10:00:00Z"), request.getTimestamp());
        assertEquals("TEST_SIGNATURE", request.getSignature());
    }
}
