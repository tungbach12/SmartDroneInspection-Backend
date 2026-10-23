package com.smartdroneinspection.subscriptions.api.dto;

import java.util.List;

public record SubscriptionCurrentResponse(
    SubscriptionResponse currentSubscription,
    List<SubscriptionResponse> history,
    ContractTermsResponse termsPreview,
    PaymentInstructionsResponse paymentInstructions) {}
