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
            @Valid @RequestBody SyncTransactionRequest request,
            org.springframework.security.core.Authentication authentication) {
        
        if (authentication != null && authentication.getPrincipal() instanceof com.lastmilebanking.backend.entity.User) {
            com.lastmilebanking.backend.entity.User user = (com.lastmilebanking.backend.entity.User) authentication.getPrincipal();
            if (user.getUserId() != null && !user.getUserId().equals(request.getSenderId())) {
                throw new org.springframework.security.access.AccessDeniedException("You are not authorized to initiate transactions for this sender");
            }
        }
        
        SyncTransactionResponse response = transactionService.processTransaction(request);
        
        if (response.getStatus() == TransactionStatus.DUPLICATE) {
            return ResponseEntity.ok(response);
        }
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<com.lastmilebanking.backend.dto.response.SettlementResponse> getTransactionStatus(
            @PathVariable String transactionId,
            org.springframework.security.core.Authentication authentication) {
            
        com.lastmilebanking.backend.entity.Transaction tx = transactionService.getTransaction(transactionId);
        
        com.lastmilebanking.backend.entity.User user = (com.lastmilebanking.backend.entity.User) authentication.getPrincipal();
        if (!user.getUserId().equals(tx.getSenderId()) && !user.getUserId().equals(tx.getReceiverId())) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to view this transaction");
        }
        
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
    public ResponseEntity<com.lastmilebanking.backend.dto.response.SettlementResponse> settleTransaction(
            @PathVariable String transactionId,
            org.springframework.security.core.Authentication authentication) {
            
        com.lastmilebanking.backend.entity.Transaction tx = transactionService.getTransaction(transactionId);
        com.lastmilebanking.backend.entity.User user = (com.lastmilebanking.backend.entity.User) authentication.getPrincipal();
        if (!user.getUserId().equals(tx.getSenderId()) && !user.getUserId().equals(tx.getReceiverId())) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to settle this transaction");
        }
            
        com.lastmilebanking.backend.dto.response.SettlementResponse response = settlementService.settle(transactionId);
        return ResponseEntity.ok(response);
    }
}
