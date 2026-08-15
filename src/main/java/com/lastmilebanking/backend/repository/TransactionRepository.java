package com.lastmilebanking.backend.repository;

import com.lastmilebanking.backend.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTransactionId(String transactionId);
    boolean existsByTransactionId(String transactionId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT t FROM Transaction t WHERE t.transactionId = :transactionId")
    Optional<Transaction> findByTransactionIdForUpdate(@org.springframework.data.repository.query.Param("transactionId") String transactionId);
}
