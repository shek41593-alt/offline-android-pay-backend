package com.lastmilebanking.backend.repository;

import com.lastmilebanking.backend.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTransactionId(String transactionId);
    boolean existsByTransactionId(String transactionId);
}
