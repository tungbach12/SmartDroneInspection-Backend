package com.smartdroneinspection.subscriptions.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePayPalOrderRequest(
    @NotNull(message = "Subscription ID is required") UUID subscriptionId) {}
