package com.lastmilebanking.backend.repository;

import com.lastmilebanking.backend.entity.Wallet;
import com.lastmilebanking.backend.entity.WalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
public class WalletRepositoryTest {

    @Autowired
    private WalletRepository walletRepository;

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll();
    }

    private Wallet createWalletContext(String walletId, String userId) {
        Wallet w = new Wallet();
        w.setWalletId(walletId);
        w.setUserId(userId);
        w.setCurrency("INR");
        w.setBalance(new BigDecimal("1000.00"));
        w.setStatus(WalletStatus.ACTIVE);
        return w;
    }

    @Test
    void testSaveAndFindByWalletId() {
        Wallet w = createWalletContext("W-1", "U-1");
        walletRepository.save(w);

        Optional<Wallet> found = walletRepository.findByWalletId("W-1");
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo("U-1");
        assertThat(found.get().getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void testFindByUserId() {
        walletRepository.save(createWalletContext("W-2", "USER-X"));
        walletRepository.save(createWalletContext("W-3", "USER-X"));

        List<Wallet> found = walletRepository.findByUserId("USER-X");
        assertThat(found).hasSize(2);
    }

    @Test
    void testDuplicateWalletIdThrowsException() {
        walletRepository.save(createWalletContext("W-DUP", "U-1"));

        Wallet duplicate = createWalletContext("W-DUP", "U-2");
        assertThrows(DataIntegrityViolationException.class, () -> {
            walletRepository.saveAndFlush(duplicate);
        });
    }

    @Test
    void testMissingWallet() {
        Optional<Wallet> found = walletRepository.findByWalletId("UNKNOWN");
        assertThat(found).isEmpty();
        assertThat(walletRepository.existsByWalletId("UNKNOWN")).isFalse();
    }
}
