package com.lastmilebanking.backend.service;

import com.lastmilebanking.backend.dto.request.SyncTransactionRequest;
import com.lastmilebanking.backend.dto.response.SyncTransactionResponse;
import com.lastmilebanking.backend.entity.Transaction;
import com.lastmilebanking.backend.entity.TransactionStatus;
import com.lastmilebanking.backend.exception.IdempotencyConflictException;
import com.lastmilebanking.backend.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    private SyncTransactionRequest validRequest;
    private Transaction existingTx;

    @BeforeEach
    void setUp() {
        validRequest = new SyncTransactionRequest();
        validRequest.setTransactionId("TX001");
        validRequest.setSenderId("SENDER1");
        validRequest.setReceiverId("RECEIVER1");
        validRequest.setAmount(new BigDecimal("100.50"));
        validRequest.setCurrency("INR");
        validRequest.setPaymentMode("QR");
        validRequest.setTimestamp(Instant.parse("2026-08-14T10:00:00Z"));
        validRequest.setSignature("TEST_SIG");

        existingTx = new Transaction();
        existingTx.setTransactionId("TX001");
        existingTx.setSenderId("SENDER1");
        existingTx.setReceiverId("RECEIVER1");
        existingTx.setAmount(new BigDecimal("100.50"));
        existingTx.setCurrency("INR");
        existingTx.setPaymentMode("QR");
        existingTx.setTransactionTimestamp(Instant.parse("2026-08-14T10:00:00Z"));
        existingTx.setSignature("TEST_SIG");
        existingTx.setStatus(TransactionStatus.RECEIVED);
    }

    @Test
    void processTransaction_validNew_createsAndReturnsReceived() {
        when(transactionRepository.findByTransactionId(validRequest.getTransactionId())).thenReturn(Optional.empty());

        SyncTransactionResponse response = transactionService.processTransaction(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.RECEIVED);
        assertThat(response.getTransactionId()).isEqualTo(validRequest.getTransactionId());

        verify(transactionRepository, times(1)).saveAndFlush(any(Transaction.class));
    }

    @Test
    void testProcessTransaction_concurrentRace_handlesDuplicateSafely() {
        Transaction existingTx = new Transaction();
        existingTx.setTransactionId("B6-TX-RACE");
        existingTx.setSenderId("A");
        existingTx.setReceiverId("B");
        existingTx.setAmount(new BigDecimal("99.99"));
        existingTx.setCurrency("INR");
        existingTx.setPaymentMode("QR");
        existingTx.setTransactionTimestamp(validRequest.getTimestamp());
        existingTx.setSignature("SIG-RACE");

        when(transactionRepository.findByTransactionId("B6-TX-RACE"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingTx));

        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        validRequest.setTransactionId("B6-TX-RACE");
        validRequest.setSenderId("A");
        validRequest.setReceiverId("B");
        validRequest.setAmount(new BigDecimal("99.99"));
        validRequest.setCurrency("INR");
        validRequest.setPaymentMode("QR");
        validRequest.setSignature("SIG-RACE");

        SyncTransactionResponse response = transactionService.processTransaction(validRequest);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.DUPLICATE);
        verify(transactionRepository, times(2)).findByTransactionId("B6-TX-RACE");
        verify(transactionRepository, times(1)).saveAndFlush(any(Transaction.class));
    }

    @Test
    void processTransaction_exactDuplicate_returnsDuplicateAndNotSaved() {
        when(transactionRepository.findByTransactionId(validRequest.getTransactionId())).thenReturn(Optional.of(existingTx));

        SyncTransactionResponse response = transactionService.processTransaction(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.DUPLICATE);
        assertThat(response.getTransactionId()).isEqualTo(validRequest.getTransactionId());

        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void processTransaction_sameIdDifferentAmount_throwsConflict() {
        existingTx.setAmount(new BigDecimal("200.00"));
        when(transactionRepository.findByTransactionId(validRequest.getTransactionId())).thenReturn(Optional.of(existingTx));

        assertThrows(IdempotencyConflictException.class, () -> {
            transactionService.processTransaction(validRequest);
        });

        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }
    
    @Test
    void processTransaction_sameIdDifferentSenderId_throwsConflict() {
        existingTx.setSenderId("DIFFERENT_SENDER");
        when(transactionRepository.findByTransactionId(validRequest.getTransactionId())).thenReturn(Optional.of(existingTx));

        assertThrows(IdempotencyConflictException.class, () -> {
            transactionService.processTransaction(validRequest);
        });

        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void processTransaction_sameIdDifferentReceiver_throwsConflict() {
        existingTx.setReceiverId("DIFF");
        when(transactionRepository.findByTransactionId(validRequest.getTransactionId())).thenReturn(Optional.of(existingTx));

        assertThrows(IdempotencyConflictException.class, () -> {
            transactionService.processTransaction(validRequest);
        });

        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void processTransaction_sameIdDifferentCurrency_throwsConflict() {
        existingTx.setCurrency("USD");
        when(transactionRepository.findByTransactionId(validRequest.getTransactionId())).thenReturn(Optional.of(existingTx));

        assertThrows(IdempotencyConflictException.class, () -> {
            transactionService.processTransaction(validRequest);
        });
        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void processTransaction_sameIdDifferentPaymentMode_throwsConflict() {
        existingTx.setPaymentMode("SMS");
        when(transactionRepository.findByTransactionId(validRequest.getTransactionId())).thenReturn(Optional.of(existingTx));

        assertThrows(IdempotencyConflictException.class, () -> {
            transactionService.processTransaction(validRequest);
        });
        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }
    
    @Test
    void processTransaction_sameIdDifferentTimestamp_throwsConflict() {
        existingTx.setTransactionTimestamp(Instant.parse("2026-08-14T11:00:00Z"));
        when(transactionRepository.findByTransactionId(validRequest.getTransactionId())).thenReturn(Optional.of(existingTx));

        assertThrows(IdempotencyConflictException.class, () -> {
            transactionService.processTransaction(validRequest);
        });
        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void processTransaction_sameIdDifferentSignature_throwsConflict() {
        existingTx.setSignature("diff_sig");
        when(transactionRepository.findByTransactionId(validRequest.getTransactionId())).thenReturn(Optional.of(existingTx));

        assertThrows(IdempotencyConflictException.class, () -> {
            transactionService.processTransaction(validRequest);
        });
        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void processTransaction_concurrentInsertRace_handlesDuplicateSafely() {
        when(transactionRepository.findByTransactionId(validRequest.getTransactionId()))
            .thenReturn(Optional.empty()) // First call: no record
            .thenReturn(Optional.of(existingTx)); // Second call after constraint failure: record exists!

        when(transactionRepository.saveAndFlush(any(Transaction.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        SyncTransactionResponse response = transactionService.processTransaction(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.DUPLICATE);
        
        verify(transactionRepository, times(1)).saveAndFlush(any(Transaction.class));
        verify(transactionRepository, times(2)).findByTransactionId(validRequest.getTransactionId());
    }
}
