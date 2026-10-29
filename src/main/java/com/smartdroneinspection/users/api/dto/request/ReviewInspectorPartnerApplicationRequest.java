package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewInspectorPartnerApplicationRequest(
    @NotBlank @Size(max = 1000) String partnerContractFileKey,
    @Size(max = 2000) String reviewNote) {}
