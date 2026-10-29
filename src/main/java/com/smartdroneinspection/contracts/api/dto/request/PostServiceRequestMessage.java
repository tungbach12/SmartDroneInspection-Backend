package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostServiceRequestMessage(@NotBlank @Size(max = 4000) String body) {}
