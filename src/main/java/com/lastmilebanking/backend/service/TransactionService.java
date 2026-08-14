package com.lastmilebanking.backend.service;

import com.lastmilebanking.backend.dto.request.SyncTransactionRequest;
import com.lastmilebanking.backend.dto.response.SyncTransactionResponse;
import com.lastmilebanking.backend.entity.Transaction;
import com.lastmilebanking.backend.entity.TransactionStatus;
import com.lastmilebanking.backend.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public SyncTransactionResponse processTransaction(SyncTransactionRequest request) {
        if (transactionRepository.existsByTransactionId(request.getTransactionId())) {
            return new SyncTransactionResponse(
                    request.getTransactionId(),
                    TransactionStatus.DUPLICATE,
                    "Transaction already exists"
            );
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

        transactionRepository.save(transaction);

        return new SyncTransactionResponse(
                transaction.getTransactionId(),
                transaction.getStatus(),
                "Transaction received successfully"
        );
    }
}
