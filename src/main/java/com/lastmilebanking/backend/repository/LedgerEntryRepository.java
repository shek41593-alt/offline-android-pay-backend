package com.lastmilebanking.backend.repository;

import com.lastmilebanking.backend.entity.LedgerEntry;
import com.lastmilebanking.backend.entity.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Optional<LedgerEntry> findByLedgerEntryId(String ledgerEntryId);

    List<LedgerEntry> findByTransactionId(String transactionId);

    List<LedgerEntry> findByWalletId(String walletId);

    boolean existsByLedgerEntryId(String ledgerEntryId);
    
    Optional<LedgerEntry> findByTransactionIdAndWalletIdAndEntryType(
            String transactionId, String walletId, LedgerEntryType entryType);
}
