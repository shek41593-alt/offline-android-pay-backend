package com.lastmilebanking.backend.service;

import com.lastmilebanking.backend.entity.Wallet;
import com.lastmilebanking.backend.entity.WalletStatus;
import com.lastmilebanking.backend.exception.InsufficientWalletBalanceException;
import com.lastmilebanking.backend.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional
    public Wallet createWallet(String walletId, String userId, String currency, BigDecimal initialBalance) {
        if (walletId == null || userId == null || currency == null || initialBalance == null) {
            throw new IllegalArgumentException("Wallet fields cannot be null");
        }
        if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }
        if (walletRepository.existsByWalletId(walletId)) {
            throw new IllegalArgumentException("Wallet ID already exists");
        }

        Wallet wallet = new Wallet();
        wallet.setWalletId(walletId);
        wallet.setUserId(userId);
        wallet.setCurrency(currency);
        wallet.setBalance(initialBalance);
        wallet.setStatus(WalletStatus.ACTIVE);

        return walletRepository.save(wallet);
    }

    public Wallet getWallet(String walletId) {
        return walletRepository.findByWalletId(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
    }

    public BigDecimal getBalance(String walletId) {
        return getWallet(walletId).getBalance();
    }

    @Transactional
    public Wallet debit(String walletId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }

        Wallet wallet = walletRepository.findByWalletIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new IllegalArgumentException("Wallet is not active");
        }

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientWalletBalanceException("Insufficient balance");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        return walletRepository.save(wallet);
    }

    @Transactional
    public Wallet credit(String walletId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }

        Wallet wallet = walletRepository.findByWalletIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new IllegalArgumentException("Wallet is not active");
        }

        wallet.setBalance(wallet.getBalance().add(amount));
        return walletRepository.save(wallet);
    }
}
