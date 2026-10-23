package com.smartdroneinspection.subscriptions.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Identifies the gateway order to capture.
 *
 * <p>Only the order id is taken from the caller. The capture id, the settled amount, the currency,
 * and the payer are read back from the payment provider, so a request that tries to assert them is
 * ignored rather than believed.
 */
public record CapturePayPalOrderRequest(
    @NotNull(message = "Subscription ID is required") UUID subscriptionId,
    @NotBlank(message = "PayPal order ID is required") String paypalOrderId) {}
