package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateDefectVerificationRequest(
    @NotBlank String resolutionStatus,
    UUID newEvidenceId,
    @Size(max = 4000) String inspectorNotes) {}
