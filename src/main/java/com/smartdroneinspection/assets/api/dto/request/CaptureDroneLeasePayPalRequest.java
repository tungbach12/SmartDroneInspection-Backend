package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CaptureDroneLeasePayPalRequest(@NotBlank @Size(max = 64) String paypalOrderId) {}
