package com.smartdroneinspection.assets.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AssetResponse(
    UUID id,
    String code,
    String name,
    String description,
    String locationText,
    BigDecimal latitude,
    BigDecimal longitude,
    String status,
    UUID categoryId,
    Instant createdAt) {}
