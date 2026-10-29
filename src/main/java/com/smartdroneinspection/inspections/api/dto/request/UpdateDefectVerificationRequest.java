package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record UpdateDefectVerificationRequest(
    @NotBlank String resolutionStatus, UUID newEvidenceId, String inspectorNotes) {}
