package com.smartdroneinspection.subscriptions.api.dto;

import java.math.BigDecimal;

public record PayPalOrderResponse(
    String paypalOrderId, String status, BigDecimal amount, String currency, String approvalUrl) {}
