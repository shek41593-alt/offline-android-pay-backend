package com.lastmilebanking.backend.service;

import com.lastmilebanking.backend.entity.Transaction;
import com.lastmilebanking.backend.entity.TransactionStatus;
import com.lastmilebanking.backend.entity.Wallet;
import com.lastmilebanking.backend.exception.CurrencyMismatchException;
import com.lastmilebanking.backend.exception.SameWalletPaymentException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PaymentOrchestrationService {

    private final TransactionService transactionService;
    private final WalletService walletService;
    private final LedgerService ledgerService;

    public PaymentOrchestrationService(TransactionService transactionService,
                                       WalletService walletService,
                                       LedgerService ledgerService) {
        this.transactionService = transactionService;
        this.walletService = walletService;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public Transaction executePayment(String transactionId, String senderId, String receiverId, BigDecimal amount, String currency) {
        Transaction transaction = transactionService.getTransactionForUpdate(transactionId);

        // 1. Validate transaction/idempotency
        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            return transaction; // Idempotent return
        }

        // 2 & 3. Locate wallets
        Wallet senderWallet = walletService.getWalletByUserId(senderId);
        Wallet receiverWallet = walletService.getWalletByUserId(receiverId);

        // 4. Validate both wallets
        if (senderWallet.getWalletId().equals(receiverWallet.getWalletId())) {
            throw new SameWalletPaymentException("Sender and receiver wallets cannot be the same");
        }

        if (!senderWallet.getCurrency().equals(currency) || !receiverWallet.getCurrency().equals(currency)) {
            throw new CurrencyMismatchException("Currency mismatch between transaction and wallets");
        }

        // Execute payment logic
        try {
            // 5. Debit sender
            walletService.debit(senderWallet.getWalletId(), amount);

            // 6. Credit receiver
            walletService.credit(receiverWallet.getWalletId(), amount);

            // 7. Create sender DEBIT ledger entry
            ledgerService.createDebitEntry(transactionId, senderWallet.getWalletId(), amount, currency);

            // 8. Create receiver CREDIT ledger entry
            ledgerService.createCreditEntry(transactionId, receiverWallet.getWalletId(), amount, currency);

            // 9. Update transaction state
            transaction.setStatus(TransactionStatus.COMPLETED);
            transactionService.updateTransactionStatus(transactionId, TransactionStatus.COMPLETED);

            // 10. Commit atomically happens via @Transactional
            return transaction;
        } catch (Exception e) {
            // Because it's transactional, everything rolls back. We just want to mark transaction as FAILED?
            // Actually, if we update the status, and it rolls back, the status update rolls back too!
            // The prompt says: "Expected: transaction remains in its pre-execution state". So rolling back the tx status is correct.
            throw e;
        }
    }
}
