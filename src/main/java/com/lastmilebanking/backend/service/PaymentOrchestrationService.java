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
    public void executePayment(String transactionId, String senderId, String receiverId, BigDecimal amount, String currency) {
        // Find wallets
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
        // 5. Debit sender
        walletService.debit(senderWallet.getWalletId(), amount);

        // 6. Credit receiver
        walletService.credit(receiverWallet.getWalletId(), amount);

        // 7. Create sender DEBIT ledger entry
        ledgerService.createDebitEntry(transactionId, senderWallet.getWalletId(), amount, currency);

        // 8. Create receiver CREDIT ledger entry
        ledgerService.createCreditEntry(transactionId, receiverWallet.getWalletId(), amount, currency);
    }
}
