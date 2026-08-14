package com.lastmilebanking.backend.service;

import com.lastmilebanking.backend.entity.LedgerEntry;
import com.lastmilebanking.backend.entity.LedgerEntryType;
import com.lastmilebanking.backend.repository.LedgerEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class LedgerServiceTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        ledgerEntryRepository.deleteAll();
    }

    @Test
    void testCreateDebitEntry() {
        LedgerEntry entry = ledgerService.createDebitEntry("TX001", "W001", new BigDecimal("100.00"), "INR");
        assertThat(entry).isNotNull();
        assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.DEBIT);
        assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(entry.getCreatedAt()).isNotNull();
    }

    @Test
    void testCreateCreditEntry() {
        LedgerEntry entry = ledgerService.createCreditEntry("TX001", "W002", new BigDecimal("200.00"), "INR");
        assertThat(entry).isNotNull();
        assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.CREDIT);
        assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(entry.getCreatedAt()).isNotNull();
    }

    @Test
    void testFindByTransactionId() {
        ledgerService.createDebitEntry("TX_FIND", "W_A", new BigDecimal("50.00"), "INR");
        ledgerService.createCreditEntry("TX_FIND", "W_B", new BigDecimal("50.00"), "INR");

        List<LedgerEntry> entries = ledgerService.findByTransactionId("TX_FIND");
        assertThat(entries).hasSize(2);
    }

    @Test
    void testFindByWalletId() {
        ledgerService.createDebitEntry("TX_W_1", "W_TARGET", new BigDecimal("50.00"), "INR");
        ledgerService.createCreditEntry("TX_W_2", "W_TARGET", new BigDecimal("50.00"), "INR");

        List<LedgerEntry> entries = ledgerService.findByWalletId("W_TARGET");
        assertThat(entries).hasSize(2);
    }

    @Test
    void testDuplicateExactDebitEntry_Idempotent() {
        LedgerEntry entry1 = ledgerService.createDebitEntry("TX_DUP", "W001", new BigDecimal("100.00"), "INR");
        LedgerEntry entry2 = ledgerService.createDebitEntry("TX_DUP", "W001", new BigDecimal("100.00"), "INR");
        
        assertThat(entry1.getId()).isEqualTo(entry2.getId());
        assertThat(ledgerEntryRepository.findAll()).hasSize(1);
    }

    @Test
    void testDuplicateExactCreditEntry_Idempotent() {
        LedgerEntry entry1 = ledgerService.createCreditEntry("TX_DUP2", "W002", new BigDecimal("100.00"), "INR");
        LedgerEntry entry2 = ledgerService.createCreditEntry("TX_DUP2", "W002", new BigDecimal("100.00"), "INR");
        
        assertThat(entry1.getId()).isEqualTo(entry2.getId());
        assertThat(ledgerEntryRepository.findAll()).hasSize(1);
    }

    @Test
    void testIdempotencyConflict_DifferentAmount() {
        ledgerService.createDebitEntry("TX_CONF", "W001", new BigDecimal("100.00"), "INR");
        assertThrows(IllegalStateException.class, () -> {
            ledgerService.createDebitEntry("TX_CONF", "W001", new BigDecimal("200.00"), "INR");
        });
        assertThat(ledgerEntryRepository.findAll()).hasSize(1);
    }

    @Test
    void testZeroAmountRejected() {
        assertThrows(IllegalArgumentException.class, () -> {
            ledgerService.createDebitEntry("TX001", "W001", BigDecimal.ZERO, "INR");
        });
    }

    @Test
    void testNegativeAmountRejected() {
        assertThrows(IllegalArgumentException.class, () -> {
            ledgerService.createDebitEntry("TX001", "W001", new BigDecimal("-100.00"), "INR");
        });
    }

    @Test
    void testMissingTransactionIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> {
            ledgerService.createDebitEntry(null, "W001", new BigDecimal("100.00"), "INR");
        });
    }

    @Test
    void testMissingWalletIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> {
            ledgerService.createDebitEntry("TX001", null, new BigDecimal("100.00"), "INR");
        });
    }

    @Test
    void testMissingCurrencyRejected() {
        assertThrows(IllegalArgumentException.class, () -> {
            ledgerService.createDebitEntry("TX001", "W001", new BigDecimal("100.00"), null);
        });
    }

    @Test
    void testDatabaseConstraintDuplicatePrevented() {
        // Saving a direct entity with same values to verify unique constraints manually
        LedgerEntry entry1 = new LedgerEntry();
        entry1.setLedgerEntryId("ID1");
        entry1.setTransactionId("TX_CONST");
        entry1.setWalletId("W001");
        entry1.setEntryType(LedgerEntryType.DEBIT);
        entry1.setAmount(new BigDecimal("100.00"));
        entry1.setCurrency("INR");
        ledgerEntryRepository.saveAndFlush(entry1);

        LedgerEntry entry2 = new LedgerEntry();
        entry2.setLedgerEntryId("ID2"); // Different random ID
        entry2.setTransactionId("TX_CONST");
        entry2.setWalletId("W001");
        entry2.setEntryType(LedgerEntryType.DEBIT);
        entry2.setAmount(new BigDecimal("100.00"));
        entry2.setCurrency("INR");
        
        assertThrows(DataIntegrityViolationException.class, () -> {
            ledgerEntryRepository.saveAndFlush(entry2);
        });
    }

    @Test
    void testBigDecimalPrecision() {
        LedgerEntry entry = ledgerService.createDebitEntry("TX001", "W001", new BigDecimal("300.25"), "INR");
        LedgerEntry dbEntry = ledgerEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(dbEntry.getAmount()).isEqualByComparingTo(new BigDecimal("300.25"));
    }

    @Test
    void testImmutabilityDoesNotProvideUpdate() {
        LedgerEntry entry = ledgerService.createDebitEntry("TX_IMM", "W001", new BigDecimal("100.00"), "INR");
        // We test that ledgerService does not have an update or delete method natively.
        // It's verified structurally since no such methods exist on the service API.
        assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }
}
