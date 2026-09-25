package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateAssetRequest(
    @Size(max = 200) String name,
    @Size(max = 2000) String description,
    @Size(max = 500) String locationText,
    BigDecimal latitude,
    BigDecimal longitude,
    @Size(max = 1000) String ownershipInformation) {}
