package com.lastmilebanking.backend.service;

import com.lastmilebanking.backend.entity.Wallet;
import com.lastmilebanking.backend.entity.WalletStatus;
import com.lastmilebanking.backend.exception.InsufficientWalletBalanceException;
import com.lastmilebanking.backend.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class WalletServiceTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        walletRepository.deleteAll();
    }

    @Test
    void testCreateWallet() {
        Wallet w = walletService.createWallet("W-1", "U-1", "INR", new BigDecimal("1000.00"));
        assertThat(w.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(w.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void testDuplicateWalletRejected() {
        walletService.createWallet("W-DUP", "U-1", "INR", new BigDecimal("1000.00"));
        assertThrows(IllegalArgumentException.class, () -> {
            walletService.createWallet("W-DUP", "U-2", "INR", new BigDecimal("100.00"));
        });
    }

    @Test
    void testGetBalance() {
        walletService.createWallet("W-BAL", "U-1", "INR", new BigDecimal("555.55"));
        BigDecimal balance = walletService.getBalance("W-BAL");
        assertThat(balance).isEqualByComparingTo(new BigDecimal("555.55"));
    }

    @Test
    void testDebitValidAmount() {
        walletService.createWallet("W-DEB", "U-1", "INR", new BigDecimal("1000.00"));
        Wallet updated = walletService.debit("W-DEB", new BigDecimal("300.00"));
        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("700.00"));
    }

    @Test
    void testDebitExactBalance() {
        walletService.createWallet("W-EXACT", "U-1", "INR", new BigDecimal("1000.00"));
        Wallet updated = walletService.debit("W-EXACT", new BigDecimal("1000.00"));
        assertThat(updated.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void testDebitInsufficientBalance() {
        walletService.createWallet("W-INSUFF", "U-1", "INR", new BigDecimal("1000.00"));
        assertThrows(InsufficientWalletBalanceException.class, () -> {
            walletService.debit("W-INSUFF", new BigDecimal("1001.00"));
        });
    }

    @Test
    void testNegativeDebitRejected() {
        walletService.createWallet("W-NEG-D", "U-1", "INR", new BigDecimal("1000.00"));
        assertThrows(IllegalArgumentException.class, () -> {
            walletService.debit("W-NEG-D", new BigDecimal("-100.00"));
        });
    }

    @Test
    void testZeroDebitRejected() {
        walletService.createWallet("W-ZERO-D", "U-1", "INR", new BigDecimal("1000.00"));
        assertThrows(IllegalArgumentException.class, () -> {
            walletService.debit("W-ZERO-D", BigDecimal.ZERO);
        });
    }

    @Test
    void testCredit() {
        walletService.createWallet("W-CRED", "U-1", "INR", new BigDecimal("500.00"));
        Wallet updated = walletService.credit("W-CRED", new BigDecimal("200.00"));
        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("700.00"));
    }

    @Test
    void testNegativeCreditRejected() {
        walletService.createWallet("W-NEG-C", "U-1", "INR", new BigDecimal("500.00"));
        assertThrows(IllegalArgumentException.class, () -> {
            walletService.credit("W-NEG-C", new BigDecimal("-200.00"));
        });
    }

    @Test
    void testZeroCreditRejected() {
        walletService.createWallet("W-ZERO-C", "U-1", "INR", new BigDecimal("500.00"));
        assertThrows(IllegalArgumentException.class, () -> {
            walletService.credit("W-ZERO-C", BigDecimal.ZERO);
        });
    }

    @Test
    void testSuspendedWalletDebitCredit() {
        Wallet w = walletService.createWallet("W-SUSP", "U-1", "INR", new BigDecimal("1000.00"));
        w.setStatus(WalletStatus.SUSPENDED);
        walletRepository.save(w);

        assertThrows(IllegalArgumentException.class, () -> {
            walletService.debit("W-SUSP", new BigDecimal("100.00"));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            walletService.credit("W-SUSP", new BigDecimal("100.00"));
        });
    }

    @Test
    void testConcurrentDebit() throws Exception {
        walletService.createWallet("W-CONC", "U-1", "INR", new BigDecimal("1000.00"));

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Boolean> task1 = () -> {
            try {
                walletService.debit("W-CONC", new BigDecimal("700.00"));
                return true;
            } catch (Exception e) {
                return false;
            }
        };

        Callable<Boolean> task2 = () -> {
            try {
                walletService.debit("W-CONC", new BigDecimal("700.00"));
                return true;
            } catch (Exception e) {
                return false;
            }
        };

        List<Callable<Boolean>> tasks = new ArrayList<>();
        tasks.add(task1);
        tasks.add(task2);

        List<Future<Boolean>> results = executor.invokeAll(tasks);

        int successCount = 0;
        int failureCount = 0;

        for (Future<Boolean> result : results) {
            if (result.get()) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        // Only one debit should succeed
        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(1);

        BigDecimal finalBalance = walletService.getBalance("W-CONC");
        assertThat(finalBalance).isEqualByComparingTo(new BigDecimal("300.00"));
    }
    
    @Test
    void testPrecision() {
        walletService.createWallet("W-PREC", "U-1", "INR", new BigDecimal("1000.25"));
        Wallet updated = walletService.debit("W-PREC", new BigDecimal("100.10"));
        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("900.15"));
    }
}
