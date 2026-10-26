package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartFieldSessionRequest(@NotBlank @Size(max = 2000) String preFlightChecklistNote) {}
