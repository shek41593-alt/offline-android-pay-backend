package com.lastmilebanking.backend.entity;

public enum TransactionStatus {
    RECEIVED,
    PROCESSING,
    SETTLED,
    REJECTED,
    DUPLICATE,
    FAILED
}
