package com.lastmilebanking.backend.dto.response;

import com.lastmilebanking.backend.entity.TransactionStatus;

public class SyncTransactionResponse {

    private String transactionId;
    private TransactionStatus status;
    private String message;

    public SyncTransactionResponse() {}

    public SyncTransactionResponse(String transactionId, TransactionStatus status, String message) {
        this.transactionId = transactionId;
        this.status = status;
        this.message = message;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
