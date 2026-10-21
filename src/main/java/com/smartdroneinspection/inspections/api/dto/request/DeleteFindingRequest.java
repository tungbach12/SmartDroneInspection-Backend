package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** MF3-06 rejection of a pending AI suggestion or attributed removal of an Inspector finding. */
public record DeleteFindingRequest(@NotBlank @Size(max = 2000) String reason) {}
