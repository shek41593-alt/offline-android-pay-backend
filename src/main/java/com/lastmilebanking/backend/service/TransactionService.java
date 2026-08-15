package com.lastmilebanking.backend.service;

import com.lastmilebanking.backend.dto.request.SyncTransactionRequest;
import com.lastmilebanking.backend.dto.response.SyncTransactionResponse;
import com.lastmilebanking.backend.entity.Transaction;
import com.lastmilebanking.backend.entity.TransactionStatus;
import com.lastmilebanking.backend.exception.IdempotencyConflictException;
import com.lastmilebanking.backend.repository.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public SyncTransactionResponse processTransaction(SyncTransactionRequest request) {
        Optional<Transaction> existingOpt = transactionRepository.findByTransactionId(request.getTransactionId());
        
        if (existingOpt.isPresent()) {
            return checkIdempotencyAndReturn(existingOpt.get(), request);
        }

        Transaction transaction = new Transaction();
        transaction.setTransactionId(request.getTransactionId());
        transaction.setSenderId(request.getSenderId());
        transaction.setReceiverId(request.getReceiverId());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setPaymentMode(request.getPaymentMode());
        transaction.setTransactionTimestamp(request.getTimestamp());
        transaction.setSignature(request.getSignature());
        transaction.setStatus(TransactionStatus.RECEIVED);

        try {
            transactionRepository.save(transaction);
        } catch (DataIntegrityViolationException e) {
            // Concurrent insert race condition occurred. Unique constraint failed.
            // We should fetch the one that was just inserted and check it!
            Optional<Transaction> concurrentExistingOpt = transactionRepository.findByTransactionId(request.getTransactionId());
            if (concurrentExistingOpt.isPresent()) {
                return checkIdempotencyAndReturn(concurrentExistingOpt.get(), request);
            } else {
                throw e; // If it's a difference constraint failing, rethrow.
            }
        }

        return new SyncTransactionResponse(
                transaction.getTransactionId(),
                transaction.getStatus(),
                "Transaction received successfully"
        );
    }

    private SyncTransactionResponse checkIdempotencyAndReturn(Transaction existing, SyncTransactionRequest request) {
        if (!Objects.equals(existing.getSenderId(), request.getSenderId()) ||
            !Objects.equals(existing.getReceiverId(), request.getReceiverId()) ||
            existing.getAmount().compareTo(request.getAmount()) != 0 ||
            !Objects.equals(existing.getCurrency(), request.getCurrency()) ||
            !Objects.equals(existing.getPaymentMode(), request.getPaymentMode()) ||
            !Objects.equals(existing.getTransactionTimestamp(), request.getTimestamp()) ||
            !Objects.equals(existing.getSignature(), request.getSignature())) {
            
            throw new IdempotencyConflictException("Transaction ID already exists with different transaction data");
        }

        return new SyncTransactionResponse(
                existing.getTransactionId(),
                TransactionStatus.DUPLICATE,
                "Transaction already exists"
        );
    }

    public Transaction getTransaction(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
    }

    @Transactional
    public Transaction getTransactionForUpdate(String transactionId) {
        Transaction tx = transactionRepository.findByTransactionIdForUpdate(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        // Force refresh L1 cache as a safety measure for concurrent queries
        transactionRepository.flush(); // ensure connection is active? actually no EntityManager injected here, but simple ForUpdate is enough since it's the first thing called in Orchestrator.
        return tx;
    }

    @Transactional
    public void updateTransactionStatus(String transactionId, TransactionStatus status) {
        Transaction tx = getTransaction(transactionId);
        tx.setStatus(status);
        transactionRepository.save(tx);
    }
}
