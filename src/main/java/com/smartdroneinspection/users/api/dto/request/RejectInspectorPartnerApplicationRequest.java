package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectInspectorPartnerApplicationRequest(
    @NotBlank @Size(max = 2000) String reviewNote) {}
