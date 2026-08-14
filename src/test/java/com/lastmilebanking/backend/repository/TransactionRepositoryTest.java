package com.lastmilebanking.backend.repository;

import com.lastmilebanking.backend.entity.Transaction;
import com.lastmilebanking.backend.entity.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.dao.DataIntegrityViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    public void setup() {
        transactionRepository.deleteAll();
    }

    @Test
    public void testPersistTransaction() {
        Transaction tx = new Transaction();
        tx.setTransactionId("TX001");
        tx.setSenderId("SENDER1");
        tx.setReceiverId("RECEIVER1");
        tx.setAmount(new BigDecimal("100.50"));
        tx.setCurrency("INR");
        tx.setPaymentMode("SMS");
        tx.setTransactionTimestamp(Instant.now());
        tx.setStatus(TransactionStatus.RECEIVED);

        Transaction savedTx = transactionRepository.saveAndFlush(tx);

        assertThat(savedTx.getId()).isNotNull();
        assertThat(savedTx.getCreatedAt()).isNotNull();
        
        Transaction fetchedTx = transactionRepository.findById(savedTx.getId()).orElse(null);
        assertThat(fetchedTx).isNotNull();
        assertThat(fetchedTx.getTransactionId()).isEqualTo("TX001");
        assertThat(fetchedTx.getAmount()).isEqualByComparingTo("100.50");
        assertThat(fetchedTx.getStatus()).isEqualTo(TransactionStatus.RECEIVED);
    }

    @Test
    public void testUniqueTransactionId() {
        Transaction tx1 = new Transaction();
        tx1.setTransactionId("TX002");
        tx1.setSenderId("SENDER1");
        tx1.setReceiverId("RECEIVER1");
        tx1.setAmount(new BigDecimal("50.00"));
        tx1.setCurrency("INR");
        tx1.setPaymentMode("QR");
        tx1.setStatus(TransactionStatus.COMPLETED);

        transactionRepository.saveAndFlush(tx1);

        Transaction tx2 = new Transaction();
        tx2.setTransactionId("TX002"); // Duplicate
        tx2.setSenderId("SENDER2");
        tx2.setReceiverId("RECEIVER2");
        tx2.setAmount(new BigDecimal("20.00"));
        tx2.setCurrency("INR");
        tx2.setPaymentMode("BLUETOOTH");
        tx2.setStatus(TransactionStatus.COMPLETED);

        assertThrows(DataIntegrityViolationException.class, () -> {
            transactionRepository.saveAndFlush(tx2);
        });
    }

    @Test
    public void testFindByTransactionId() {
        Transaction tx = new Transaction();
        tx.setTransactionId("TX003");
        tx.setSenderId("S1");
        tx.setReceiverId("R1");
        tx.setAmount(new BigDecimal("10.0"));
        tx.setCurrency("INR");
        tx.setPaymentMode("SMS");
        tx.setStatus(TransactionStatus.RECEIVED);

        transactionRepository.saveAndFlush(tx);

        var fetchedTx = transactionRepository.findByTransactionId("TX003");
        assertThat(fetchedTx).isPresent();
        assertThat(fetchedTx.get().getTransactionId()).isEqualTo("TX003");
    }

    @Test
    public void testFindByTransactionIdNotFound() {
        var fetchedTx = transactionRepository.findByTransactionId("TX999");
        assertThat(fetchedTx).isEmpty();
    }

    @Test
    public void testExistsByTransactionId() {
        Transaction tx = new Transaction();
        tx.setTransactionId("TX004");
        tx.setSenderId("S1");
        tx.setReceiverId("R1");
        tx.setAmount(new BigDecimal("10.0"));
        tx.setCurrency("INR");
        tx.setPaymentMode("SMS");
        tx.setStatus(TransactionStatus.RECEIVED);

        transactionRepository.saveAndFlush(tx);

        boolean exists = transactionRepository.existsByTransactionId("TX004");
        assertThat(exists).isTrue();
    }

    @Test
    public void testExistsByTransactionIdNotFound() {
        boolean exists = transactionRepository.existsByTransactionId("TX999");
        assertThat(exists).isFalse();
    }
}
