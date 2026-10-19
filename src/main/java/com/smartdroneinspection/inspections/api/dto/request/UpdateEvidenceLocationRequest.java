package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateEvidenceLocationRequest(@Size(max = 1000) String locationDescription) {}
