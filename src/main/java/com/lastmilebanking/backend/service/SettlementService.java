package com.lastmilebanking.backend.service;

import com.lastmilebanking.backend.dto.response.SettlementResponse;
import com.lastmilebanking.backend.entity.Transaction;
import com.lastmilebanking.backend.entity.TransactionStatus;
import org.springframework.stereotype.Service;

@Service
public class SettlementService {
    
    private final TransactionService transactionService;
    private final PaymentOrchestrationService paymentOrchestrationService;

    public SettlementService(TransactionService transactionService, PaymentOrchestrationService paymentOrchestrationService) {
        this.transactionService = transactionService;
        this.paymentOrchestrationService = paymentOrchestrationService;
    }

    public SettlementResponse settle(String transactionId) {
        Transaction tx = transactionService.getTransaction(transactionId);
        
        // Step 7: Retry Consideration. 
        if (tx.getStatus() == TransactionStatus.SETTLED) {
            return new SettlementResponse(tx.getTransactionId(), "SETTLED", "Already settled");
        }
        
        // This validates state transitions and blocks duplicates since it locks inside the service
        tx = transactionService.startProcessing(transactionId);
        
        try {
            paymentOrchestrationService.executePayment(
                    tx.getTransactionId(), 
                    tx.getSenderId(), 
                    tx.getReceiverId(), 
                    tx.getAmount(), 
                    tx.getCurrency()
            );
            transactionService.markSettled(transactionId);
            return new SettlementResponse(tx.getTransactionId(), "SETTLED", "Payment settled successfully");
        } catch (Exception e) {
            transactionService.markFailed(transactionId);
            throw e;
        }
    }
}
