package com.lastmilebanking.backend.exception;

public class SameWalletPaymentException extends RuntimeException {
    public SameWalletPaymentException(String message) {
        super(message);
    }
}
