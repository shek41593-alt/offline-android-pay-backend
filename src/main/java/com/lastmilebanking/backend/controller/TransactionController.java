package com.lastmilebanking.backend.controller;

import com.lastmilebanking.backend.dto.request.SyncTransactionRequest;
import com.lastmilebanking.backend.dto.response.SyncTransactionResponse;
import com.lastmilebanking.backend.entity.TransactionStatus;
import com.lastmilebanking.backend.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<SyncTransactionResponse> createTransaction(
            @Valid @RequestBody SyncTransactionRequest request) {
        
        SyncTransactionResponse response = transactionService.processTransaction(request);
        
        if (response.getStatus() == TransactionStatus.DUPLICATE) {
            return ResponseEntity.ok(response);
        }
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
