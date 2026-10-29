package com.smartdroneinspection.shared;

import java.math.BigDecimal;
import java.util.UUID;

/** Cross-module event requesting creation of the customer's first asset in the same transaction. */
public record CustomerRegistrationEvent(
    UUID organizationId,
    UUID customerId,
    UUID categoryId,
    String assetCode,
    String assetName,
    String description,
    String locationText,
    BigDecimal latitude,
    BigDecimal longitude,
    String ownershipInformation) {}
