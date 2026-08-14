package com.lastmilebanking.backend.service;

import com.lastmilebanking.backend.dto.request.SyncTransactionRequest;
import com.lastmilebanking.backend.dto.response.SyncTransactionResponse;
import com.lastmilebanking.backend.entity.Transaction;
import com.lastmilebanking.backend.entity.TransactionStatus;
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

    @BeforeEach
    void setUp() {
        validRequest = new SyncTransactionRequest();
        validRequest.setTransactionId("TX001");
        validRequest.setSenderId("SENDER1");
        validRequest.setReceiverId("RECEIVER1");
        validRequest.setAmount(new BigDecimal("100.50"));
        validRequest.setCurrency("INR");
        validRequest.setPaymentMode("QR");
        validRequest.setTimestamp(Instant.now());
        validRequest.setSignature("TEST_SIG");
    }

    @Test
    void processTransaction_validNew_createsAndReturnsReceived() {
        when(transactionRepository.existsByTransactionId(validRequest.getTransactionId())).thenReturn(false);

        SyncTransactionResponse response = transactionService.processTransaction(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.RECEIVED);
        assertThat(response.getTransactionId()).isEqualTo(validRequest.getTransactionId());

        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void processTransaction_duplicate_returnsDuplicateAndNotSaved() {
        when(transactionRepository.existsByTransactionId(validRequest.getTransactionId())).thenReturn(true);

        SyncTransactionResponse response = transactionService.processTransaction(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.DUPLICATE);
        assertThat(response.getTransactionId()).isEqualTo(validRequest.getTransactionId());

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void processTransaction_fieldMappingAndInitialStatus_correct() {
        when(transactionRepository.existsByTransactionId(validRequest.getTransactionId())).thenReturn(false);

        transactionService.processTransaction(validRequest);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());

        Transaction savedTx = txCaptor.getValue();
        assertThat(savedTx.getTransactionId()).isEqualTo(validRequest.getTransactionId());
        assertThat(savedTx.getSenderId()).isEqualTo(validRequest.getSenderId());
        assertThat(savedTx.getReceiverId()).isEqualTo(validRequest.getReceiverId());
        assertThat(savedTx.getAmount()).isEqualByComparingTo(validRequest.getAmount());
        assertThat(savedTx.getCurrency()).isEqualTo(validRequest.getCurrency());
        assertThat(savedTx.getPaymentMode()).isEqualTo(validRequest.getPaymentMode());
        assertThat(savedTx.getTransactionTimestamp()).isEqualTo(validRequest.getTimestamp());
        assertThat(savedTx.getSignature()).isEqualTo(validRequest.getSignature());
        assertThat(savedTx.getStatus()).isEqualTo(TransactionStatus.RECEIVED);
    }

    @Test
    void processTransaction_repositoryFailure_propagatesException() {
        when(transactionRepository.existsByTransactionId(validRequest.getTransactionId())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class)))
                .thenThrow(new DataIntegrityViolationException("Database constraint violated"));

        assertThrows(DataIntegrityViolationException.class, () -> {
            transactionService.processTransaction(validRequest);
        });

        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }
}
