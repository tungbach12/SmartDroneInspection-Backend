package com.smartdroneinspection.assets.api.dto.response;

public record PayPalLeaseOrderResponse(String orderId, String status, String approvalUrl) {}
