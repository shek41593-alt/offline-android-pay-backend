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
    private final com.lastmilebanking.backend.service.SettlementService settlementService;

    public TransactionController(TransactionService transactionService, com.lastmilebanking.backend.service.SettlementService settlementService) {
        this.transactionService = transactionService;
        this.settlementService = settlementService;
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

    @GetMapping("/{transactionId}")
    public ResponseEntity<com.lastmilebanking.backend.dto.response.SettlementResponse> getTransactionStatus(@PathVariable String transactionId) {
        com.lastmilebanking.backend.entity.Transaction tx = transactionService.getTransaction(transactionId);
        
        String msg = "Transaction is in state: " + tx.getStatus();
        if (tx.getStatus() == TransactionStatus.SETTLED) {
            msg = "Payment settled successfully";
        }
        return ResponseEntity.ok(new com.lastmilebanking.backend.dto.response.SettlementResponse(
                tx.getTransactionId(),
                tx.getStatus().name(),
                msg
        ));
    }

    @PostMapping("/{transactionId}/settle")
    public ResponseEntity<com.lastmilebanking.backend.dto.response.SettlementResponse> settleTransaction(@PathVariable String transactionId) {
        com.lastmilebanking.backend.dto.response.SettlementResponse response = settlementService.settle(transactionId);
        return ResponseEntity.ok(response);
    }
}
