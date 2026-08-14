package com.lastmilebanking.backend.service;

import com.lastmilebanking.backend.entity.LedgerEntry;
import com.lastmilebanking.backend.entity.LedgerEntryType;
import com.lastmilebanking.backend.repository.LedgerEntryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public LedgerEntry createDebitEntry(String transactionId, String walletId, BigDecimal amount, String currency) {
        return createEntry(transactionId, walletId, amount, currency, LedgerEntryType.DEBIT);
    }

    @Transactional
    public LedgerEntry createCreditEntry(String transactionId, String walletId, BigDecimal amount, String currency) {
        return createEntry(transactionId, walletId, amount, currency, LedgerEntryType.CREDIT);
    }

    private LedgerEntry createEntry(String transactionId, String walletId, BigDecimal amount, String currency, LedgerEntryType type) {
        if (transactionId == null || walletId == null || amount == null || currency == null || type == null) {
            throw new IllegalArgumentException("Ledger entry fields cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Ledger entry amount must be positive");
        }

        Optional<LedgerEntry> existing = ledgerEntryRepository.findByTransactionIdAndWalletIdAndEntryType(transactionId, walletId, type);
        
        if (existing.isPresent()) {
            return checkIdempotency(existing.get(), amount, currency);
        }

        LedgerEntry entry = new LedgerEntry();
        entry.setLedgerEntryId("LEDGER-" + transactionId + "-" + walletId + "-" + type.name());
        entry.setTransactionId(transactionId);
        entry.setWalletId(walletId);
        entry.setAmount(amount);
        entry.setCurrency(currency);
        entry.setEntryType(type);

        try {
            return ledgerEntryRepository.save(entry);
        } catch (DataIntegrityViolationException e) {
            Optional<LedgerEntry> concurrentOpt = ledgerEntryRepository.findByTransactionIdAndWalletIdAndEntryType(transactionId, walletId, type);
            if (concurrentOpt.isPresent()) {
                return checkIdempotency(concurrentOpt.get(), amount, currency);
            } else {
                throw e; // Rethrow if it's another constraint
            }
        }
    }

    private LedgerEntry checkIdempotency(LedgerEntry existing, BigDecimal amount, String currency) {
        if (existing.getAmount().compareTo(amount) != 0 || !existing.getCurrency().equals(currency)) {
            // Can be mapped to IdempotencyConflictException later. For now, illegal argument to fail validation.
            throw new IllegalStateException("IDEMPOTENCY_CONFLICT: Ledger entry conflict on reused transaction/wallet/type");
        }
        return existing;
    }

    public List<LedgerEntry> findByTransactionId(String transactionId) {
        return ledgerEntryRepository.findByTransactionId(transactionId);
    }

    public List<LedgerEntry> findByWalletId(String walletId) {
        return ledgerEntryRepository.findByWalletId(walletId);
    }
}
