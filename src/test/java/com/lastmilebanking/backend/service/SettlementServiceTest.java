package com.lastmilebanking.backend.service;

import com.lastmilebanking.backend.dto.request.SyncTransactionRequest;
import com.lastmilebanking.backend.dto.response.SettlementResponse;
import com.lastmilebanking.backend.entity.Transaction;
import com.lastmilebanking.backend.entity.TransactionStatus;
import com.lastmilebanking.backend.entity.Wallet;
import com.lastmilebanking.backend.entity.WalletStatus;
import com.lastmilebanking.backend.exception.CurrencyMismatchException;
import com.lastmilebanking.backend.exception.InsufficientWalletBalanceException;
import com.lastmilebanking.backend.exception.SameWalletPaymentException;
import com.lastmilebanking.backend.repository.LedgerEntryRepository;
import com.lastmilebanking.backend.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
public class SettlementServiceTest {

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private TransactionService transactionService;

    @MockitoSpyBean
    private WalletService walletServiceSpy;

    @MockitoSpyBean
    private LedgerService ledgerServiceSpy;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private com.lastmilebanking.backend.repository.TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        walletRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        ledgerEntryRepository.deleteAll();
        walletRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    private void prepareTransaction(String txId, String sender, String receiver, BigDecimal amount, String currency) {
        SyncTransactionRequest req = new SyncTransactionRequest();
        req.setTransactionId(txId);
        req.setSenderId(sender);
        req.setReceiverId(receiver);
        req.setAmount(amount);
        req.setCurrency(currency);
        req.setPaymentMode("QR");
        req.setTimestamp(Instant.now());
        req.setSignature("SIG");
        transactionService.processTransaction(req);
    }

    private Wallet prepareWallet(String walletId, String userId, BigDecimal amt, String currency) {
        return walletServiceSpy.createWallet(walletId, userId, currency, amt);
    }

    @Test
    void testSuccessfulPayment() {
        prepareTransaction("TX_OK", "U1", "U2", new BigDecimal("300.00"), "INR");
        prepareWallet("W1", "U1", new BigDecimal("1000.00"), "INR");
        prepareWallet("W2", "U2", new BigDecimal("500.00"), "INR");

        SettlementResponse result = settlementService.settle("TX_OK");

        assertThat(result.getStatus()).isEqualTo("SETTLED");
        assertThat(transactionService.getTransaction("TX_OK").getStatus()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(walletServiceSpy.getBalance("W1")).isEqualByComparingTo(new BigDecimal("700.00"));
        assertThat(walletServiceSpy.getBalance("W2")).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(ledgerEntryRepository.findByTransactionId("TX_OK")).hasSize(2);
    }

    @Test
    void testExactBalancePayment() {
        prepareTransaction("TX_EXB", "U1", "U2", new BigDecimal("1000.00"), "INR");
        prepareWallet("W1", "U1", new BigDecimal("1000.00"), "INR");
        prepareWallet("W2", "U2", new BigDecimal("500.00"), "INR");

        settlementService.settle("TX_EXB");

        assertThat(walletServiceSpy.getBalance("W1")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(walletServiceSpy.getBalance("W2")).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(transactionService.getTransaction("TX_EXB").getStatus()).isEqualTo(TransactionStatus.SETTLED);
    }

    @Test
    void testInsufficientBalance() {
        prepareTransaction("TX_INS", "U1", "U2", new BigDecimal("1001.00"), "INR");
        prepareWallet("W1", "U1", new BigDecimal("1000.00"), "INR");
        prepareWallet("W2", "U2", new BigDecimal("500.00"), "INR");

        assertThrows(InsufficientWalletBalanceException.class, () -> {
            settlementService.settle("TX_INS");
        });

        assertThat(walletServiceSpy.getBalance("W1")).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(walletServiceSpy.getBalance("W2")).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(ledgerEntryRepository.findByTransactionId("TX_INS")).isEmpty();
        
        assertThat(transactionService.getTransaction("TX_INS").getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void testSenderSuspended() {
        prepareTransaction("TX_SUSP", "U1", "U2", new BigDecimal("100.00"), "INR");
        Wallet w1 = prepareWallet("W1", "U1", new BigDecimal("1000.00"), "INR");
        w1.setStatus(WalletStatus.SUSPENDED);
        walletRepository.save(w1);
        prepareWallet("W2", "U2", new BigDecimal("500.00"), "INR");

        assertThrows(IllegalArgumentException.class, () -> {
            settlementService.settle("TX_SUSP");
        });

        assertThat(walletServiceSpy.getBalance("W1")).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(transactionService.getTransaction("TX_SUSP").getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void testSameWallet() {
        prepareTransaction("TX_SAME", "U1", "U1", new BigDecimal("100.00"), "INR");
        prepareWallet("W1", "U1", new BigDecimal("1000.00"), "INR");

        assertThrows(SameWalletPaymentException.class, () -> {
            settlementService.settle("TX_SAME");
        });
        assertThat(transactionService.getTransaction("TX_SAME").getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void testCurrencyMismatch() {
        prepareTransaction("TX_CURR", "U1", "U2", new BigDecimal("100.00"), "USD");
        prepareWallet("W1", "U1", new BigDecimal("1000.00"), "INR");
        prepareWallet("W2", "U2", new BigDecimal("500.00"), "INR");

        assertThrows(CurrencyMismatchException.class, () -> {
            settlementService.settle("TX_CURR");
        });
        assertThat(transactionService.getTransaction("TX_CURR").getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void testReceiverFailureRollback() {
        prepareTransaction("TX_RX_FAIL", "U1", "U2", new BigDecimal("100.00"), "INR");
        prepareWallet("W1", "U1", new BigDecimal("1000.00"), "INR");
        prepareWallet("W2", "U2", new BigDecimal("500.00"), "INR");

        doThrow(new RuntimeException("Simulated Receiver Credit Error"))
                .when(walletServiceSpy).credit(eq("W2"), any(BigDecimal.class));

        assertThrows(RuntimeException.class, () -> {
            settlementService.settle("TX_RX_FAIL");
        });

        // Ensure rollback (W1 balance unchanged despite being debited before credit failure)
        assertThat(walletServiceSpy.getBalance("W1")).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(ledgerEntryRepository.findByTransactionId("TX_RX_FAIL")).isEmpty();
        assertThat(transactionService.getTransaction("TX_RX_FAIL").getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void testLedgerFailureRollback() {
        prepareTransaction("TX_LD_FAIL", "U1", "U2", new BigDecimal("100.00"), "INR");
        prepareWallet("W1", "U1", new BigDecimal("1000.00"), "INR");
        prepareWallet("W2", "U2", new BigDecimal("500.00"), "INR");

        doThrow(new RuntimeException("Simulated Ledger Error"))
                .when(ledgerServiceSpy).createDebitEntry(any(), any(), any(), any());

        assertThrows(RuntimeException.class, () -> {
            settlementService.settle("TX_LD_FAIL");
        });

        // Ensure rollback
        assertThat(walletServiceSpy.getBalance("W1")).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(walletServiceSpy.getBalance("W2")).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(ledgerEntryRepository.findByTransactionId("TX_LD_FAIL")).isEmpty();
        assertThat(transactionService.getTransaction("TX_LD_FAIL").getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void testExactRetry() {
        prepareTransaction("TX_RETRY", "U1", "U2", new BigDecimal("300.00"), "INR");
        prepareWallet("W1", "U1", new BigDecimal("1000.00"), "INR");
        prepareWallet("W2", "U2", new BigDecimal("500.00"), "INR");

        settlementService.settle("TX_RETRY");
        
        // Exact Retry
        SettlementResponse result2 = settlementService.settle("TX_RETRY");
        assertThat(result2.getStatus()).isEqualTo("SETTLED");
        assertThat(result2.getMessage()).isEqualTo("Already settled");

        // Verify only deducted once
        assertThat(walletServiceSpy.getBalance("W1")).isEqualByComparingTo(new BigDecimal("700.00"));
        assertThat(ledgerEntryRepository.findByTransactionId("TX_RETRY")).hasSize(2);
    }

    @Test
    void testConcurrentSameTransaction() throws Exception {
        prepareTransaction("TX_CONC_SAME", "U1", "U2", new BigDecimal("300.00"), "INR");
        prepareWallet("W1", "U1", new BigDecimal("1000.00"), "INR");
        prepareWallet("W2", "U2", new BigDecimal("500.00"), "INR");

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Boolean> task = () -> {
            try {
                settlementService.settle("TX_CONC_SAME");
                return true;
            } catch (Exception e) {
                return false;
            }
        };

        List<Callable<Boolean>> tasks = new ArrayList<>();
        tasks.add(task);
        tasks.add(task);

        List<Future<Boolean>> results = executor.invokeAll(tasks);

        // Either one succeeds and another succeeds (idempotent), or one succeeds and second fails (conflict)
        // But regardless, money is only deducted once.
        assertThat(walletServiceSpy.getBalance("W1")).isEqualByComparingTo(new BigDecimal("700.00"));
        assertThat(ledgerEntryRepository.findByTransactionId("TX_CONC_SAME")).hasSize(2); // One debit, one credit
    }

    @Test
    void testConcurrentDifferentTransactions() throws Exception {
        String tx1 = "TX_CONC_DIFF1_" + java.util.UUID.randomUUID().toString();
        String tx2 = "TX_CONC_DIFF2_" + java.util.UUID.randomUUID().toString();

        prepareTransaction(tx1, "U1", "U2", new BigDecimal("700.00"), "INR");
        prepareTransaction(tx2, "U1", "U2", new BigDecimal("700.00"), "INR");
        prepareWallet("W1", "U1", new BigDecimal("1000.00"), "INR");
        prepareWallet("W2", "U2", new BigDecimal("500.00"), "INR");

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Boolean> task1 = () -> {
            try {
                settlementService.settle(tx1);
                return true;
            } catch (Exception e) {
                return false;
            }
        };

        Callable<Boolean> task2 = () -> {
            try {
                settlementService.settle(tx2);
                return true;
            } catch (Exception e) {
                return false;
            }
        };

        List<Callable<Boolean>> tasks = new ArrayList<>();
        tasks.add(task1);
        tasks.add(task2);

        List<Future<Boolean>> results = executor.invokeAll(tasks);

        int successes = 0;
        for (Future<Boolean> r : results) {
            if (r.get()) successes++;
        }

        // Only one transaction can succeed
        assertThat(successes).isEqualTo(1);
        assertThat(walletServiceSpy.getBalance("W1")).isEqualByComparingTo(new BigDecimal("300.00"));
        
        int failedCount = 0;
        int settledCount = 0;
        if (transactionService.getTransaction(tx1).getStatus() == TransactionStatus.FAILED) failedCount++;
        if (transactionService.getTransaction(tx2).getStatus() == TransactionStatus.FAILED) failedCount++;
        if (transactionService.getTransaction(tx1).getStatus() == TransactionStatus.SETTLED) settledCount++;
        if (transactionService.getTransaction(tx2).getStatus() == TransactionStatus.SETTLED) settledCount++;
        
        assertThat(failedCount).isEqualTo(1);
        assertThat(settledCount).isEqualTo(1);
    }
}
