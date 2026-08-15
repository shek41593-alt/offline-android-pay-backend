# Last-Mile Banking API Contract

This document thoroughly details the backend API endpoints exposed for the Android Application synchronization and payment architecture.

## Overview

- **Base URL (Local)**: `http://localhost:8080`
- **Content-Type**: `application/json`
- **Authentication**: JWT Bearer token in the `Authorization` header (`Authorization: Bearer <token>`).

---

## 1. Health & Status

### 1.1 Check Health
- **URL**: `/api/v1/health`
- **Method**: `GET`
- **Auth Required**: No (PUBLIC)
- **Description**: Verifies if the backend service is running and accessible.

**Response (200 OK):**
```json
{
  "status": "UP",
  "service": "lastmilebanking-backend"
}
```

---

## 2. Authentication

### 2.1 Register
- **URL**: `/api/v1/auth/register`
- **Method**: `POST`
- **Auth Required**: No (PUBLIC)

**Request Body:**
```json
{
  "username": "user123",
  "password": "securepassword1"
}
```
*Constraints*: Username (3-50 chars), Password (min 6 chars).

**Response (201 Created):**
```json
{
  "userId": "U...",
  "username": "user123",
  "role": "USER"
}
```

*Error Responses*:
- `400 Bad Request`: Validation errors (missing fields, too short).
- `409 Conflict`: Username already exists.

### 2.2 Login
- **URL**: `/api/v1/auth/login`
- **Method**: `POST`
- **Auth Required**: No (PUBLIC)

**Request Body:**
```json
{
  "username": "user123",
  "password": "securepassword1"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600000,
  "userId": "U...",
  "username": "user123",
  "role": "USER"
}
```

*Error Responses*:
- `401 Unauthorized`: Invalid credentials.

---

## 3. Transactions

All transaction endpoints require a valid JWT token. The `senderId` in requests must match the `userId` in the JWT token to prevent impersonation. 

### 3.1 Sync/Create Transaction
Used by the Android client to sync offline transactions.

- **URL**: `/api/v1/transactions`
- **Method**: `POST`
- **Auth Required**: Yes (PROTECTED)

**Request Body (`SyncTransactionRequest`):**
```json
{
  "transactionId": "TX001",
  "senderId": "U...",
  "receiverId": "U...",
  "amount": 100.50,
  "currency": "INR",
  "paymentMode": "QR",
  "timestamp": "2026-08-15T10:00:00Z",
  "signature": "OPTIONAL_SIG_STRING"
}
```
*Constraints*: Amount must be > 0. Valid `paymentMode` values include `QR`, `BLUETOOTH`, `SMS`. Timestamp must be a UTC ISO-8601 string. `transactionId` acts as the idempotency key.

**Response (201 Created) - First Time / Success:**
```json
{
  "transactionId": "TX001",
  "status": "RECEIVED",
  "message": "Transaction received successfully"
}
```

**Response (200 OK) - Idempotent Duplicate Request:**
Occurs when the identical transaction is synced multiple times (e.g. Android retries due to network failure).
```json
{
  "transactionId": "TX001",
  "status": "DUPLICATE",
  "message": "Transaction already exists"
}
```

*Error Responses*:
- `400 Bad Request`: Validation failure (negative amount, missing parameter, etc.).
- `401 Unauthorized`: Missing or invalid JWT.
- `403 Forbidden`: `senderId` does not match the authenticated `userId`.
- `409 Conflict`: IDEMPOTENCY_CONFLICT. Occurs if the exact `transactionId` is used but data fields (amount, receiver, etc.) differ.

### 3.2 Get Transaction Status
- **URL**: `/api/v1/transactions/{transactionId}`
- **Method**: `GET`
- **Auth Required**: Yes (PROTECTED)

**Description**:
Fetches the status of a specific transaction. The caller must be either the sender or receiver. Status can be `RECEIVED`, `PROCESSING`, `SETTLED`, or `FAILED`.

**Response (200 OK):**
```json
{
  "transactionId": "TX001",
  "status": "SETTLED",
  "message": "Payment settled successfully"
}
```

*Error Responses*:
- `403 Forbidden`: Caller is neither the sender nor the receiver.
- `404 Not Found`: Transaction does not exist.

### 3.3 Target Settle Transaction
- **URL**: `/api/v1/transactions/{transactionId}/settle`
- **Method**: `POST`
- **Auth Required**: Yes (PROTECTED)

**Response (200 OK):**
```json
{
  "transactionId": "TX001",
  "status": "SETTLED",
  "message": "Settlement successful"
}
```

*Error Responses*:
- `400 Bad Request`: Wallet rules failure (Insufficient balance, Currency mismatch).
- `403 Forbidden`: Caller is neither the sender nor the receiver.
- `404 Not Found`: Transaction does not exist.
- `409 Conflict`: Transaction is already settled or in an conflicting state.

---

## 4. Error Contract

Error payload formatting is globally enforced by `GlobalExceptionHandler`. 
Whenever a non-200/201 response occurs, the client can expect the following payload:

```json
{
  "timestamp": "2026-08-15T12:00:00.000Z",
  "status": 400,
  "error": "BAD_REQUEST",
  "message": "Missing required parameter: senderId",
  "path": "/api/v1/transactions"
}
```

### Common HTTP Error Status Mapping:
- **400 (BAD_REQUEST)**: Invalid generic errors, `VALIDATION_ERROR`, insufficient limits, malformed JSON.
- **401 (UNAUTHORIZED)**: Spring Security gate blocked execution (Invalid/missing token).
- **403 (FORBIDDEN)**: The principal passed auth execution but ownership validation disallowed access.
- **404 (NOT_FOUND)**: Resource missing.
- **409 (CONFLICT)**: `IDEMPOTENCY_CONFLICT` (same ID, wrong data) or invalid business state execution `CONFLICT`.
- **500 (INTERNAL_SERVER_ERROR)**: Fatal unaccounted server crash.

Note: No stack traces or infrastructure paths (like DB driver exceptions) will be emitted directly over endpoints.
