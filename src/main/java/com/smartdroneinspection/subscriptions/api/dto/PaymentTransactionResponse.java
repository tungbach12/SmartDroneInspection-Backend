package com.smartdroneinspection.subscriptions.api.dto;

import com.smartdroneinspection.subscriptions.domain.PaymentTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentTransactionResponse(
    UUID id,
    UUID subscriptionId,
    String paypalOrderId,
    String paypalCaptureId,
    BigDecimal amount,
    String currency,
    PaymentTransaction.Status status,
    String payerEmail,
    Instant capturedAt) {}
